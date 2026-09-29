locals {
  name             = "eventflow-${var.environment}"
  discovery_domain = "eventflow.internal"

  # One entry per deployable. `schema` is the service's Postgres schema (null = no database).
  services = {
    "api-gateway"          = { port = 8080, cpu = 512, memory = 1024, min = 2, max = 6, schema = null }
    "order-service"        = { port = 8081, cpu = 512, memory = 1024, min = 2, max = 6, schema = "orders" }
    "inventory-service"    = { port = 8082, cpu = 512, memory = 1024, min = 2, max = 6, schema = "inventory" }
    "payment-service"      = { port = 8083, cpu = 512, memory = 1024, min = 2, max = 4, schema = "payments" }
    "notification-service" = { port = 8084, cpu = 256, memory = 512, min = 1, max = 3, schema = "notifications" }
  }

  kafka_bootstrap_servers = var.create_msk ? module.msk[0].bootstrap_brokers : var.external_kafka_bootstrap_servers

  common_environment = {
    KAFKA_BOOTSTRAP_SERVERS            = local.kafka_bootstrap_servers
    EVENTFLOW_TOPICS_REPLICATIONFACTOR = tostring(var.create_msk ? min(var.msk_broker_count, 3) : 3)
    REDIS_HOST                         = module.redis.primary_endpoint
    REDIS_SSL                          = "true"
    TRACING_SAMPLING_PROBABILITY       = "0.1"
    MANAGEMENT_OTLP_TRACING_ENDPOINT   = "http://localhost:4318/v1/traces"
    JAVA_TOOL_OPTIONS                  = "-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
  }

  gateway_environment = {
    for name, svc in local.services :
    "${upper(replace(name, "-", "_"))}_URI" => "http://${name}.${local.discovery_domain}:${svc.port}"
    if name != "api-gateway"
  }

  database_url = "jdbc:postgresql://${module.rds.address}:${module.rds.port}/${module.rds.database_name}?sslmode=require"
}

module "network" {
  source             = "./modules/network"
  name               = local.name
  cidr_block         = var.vpc_cidr
  az_count           = var.az_count
  single_nat_gateway = var.single_nat_gateway
}

module "alb" {
  source              = "./modules/alb"
  name                = local.name
  vpc_id              = module.network.vpc_id
  public_subnet_ids   = module.network.public_subnet_ids
  certificate_arn     = var.certificate_arn
  target_port         = local.services["api-gateway"].port
  health_check_path   = "/actuator/health/readiness"
  deletion_protection = var.deletion_protection
}

resource "aws_ecs_cluster" "this" {
  name = local.name

  setting {
    name  = "containerInsights"
    value = "enabled"
  }
}

resource "aws_ecs_cluster_capacity_providers" "this" {
  cluster_name       = aws_ecs_cluster.this.name
  capacity_providers = ["FARGATE", "FARGATE_SPOT"]

  default_capacity_provider_strategy {
    capacity_provider = "FARGATE"
    base              = 1
    weight            = 1
  }
}

# Services find each other through Cloud Map DNS, e.g. order-service.eventflow.internal.
resource "aws_service_discovery_private_dns_namespace" "this" {
  name = local.discovery_domain
  vpc  = module.network.vpc_id
}

resource "aws_security_group" "services" {
  name        = "${local.name}-services"
  description = "EventFlow ECS tasks"
  vpc_id      = module.network.vpc_id

  ingress {
    description     = "Gateway traffic from the load balancer"
    from_port       = local.services["api-gateway"].port
    to_port         = local.services["api-gateway"].port
    protocol        = "tcp"
    security_groups = [module.alb.security_group_id]
  }

  ingress {
    description = "Service-to-service HTTP inside the cluster"
    from_port   = 8080
    to_port     = 8084
    protocol    = "tcp"
    self        = true
  }

  egress {
    description = "Kafka, Postgres, Redis, AWS APIs and the payment provider"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

module "rds" {
  source                     = "./modules/rds-postgres"
  name                       = local.name
  vpc_id                     = module.network.vpc_id
  subnet_ids                 = module.network.private_subnet_ids
  allowed_security_group_ids = [aws_security_group.services.id]
  instance_class             = var.db_instance_class
  multi_az                   = var.db_multi_az
  deletion_protection        = var.deletion_protection
}

module "redis" {
  source                     = "./modules/elasticache-redis"
  name                       = local.name
  vpc_id                     = module.network.vpc_id
  subnet_ids                 = module.network.private_subnet_ids
  allowed_security_group_ids = [aws_security_group.services.id]
  node_type                  = var.redis_node_type
}

module "msk" {
  count                      = var.create_msk ? 1 : 0
  source                     = "./modules/msk"
  name                       = local.name
  vpc_id                     = module.network.vpc_id
  subnet_ids                 = module.network.private_subnet_ids
  allowed_security_group_ids = [aws_security_group.services.id]
  broker_count               = var.msk_broker_count
  instance_type              = var.msk_instance_type
  log_retention_days         = var.log_retention_days
}

module "secrets" {
  source      = "./modules/secrets"
  environment = var.environment
}

module "observability" {
  source                  = "./modules/observability"
  name                    = local.name
  alarm_email             = var.alarm_email
  alb_arn_suffix          = module.alb.alb_arn_suffix
  target_group_arn_suffix = module.alb.target_group_arn_suffix
  db_instance_identifier  = module.rds.identifier
}

resource "aws_ecr_repository" "service" {
  for_each             = local.services
  name                 = "eventflow/${each.key}"
  image_tag_mutability = "IMMUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  encryption_configuration {
    encryption_type = "AES256"
  }
}

resource "aws_ecr_lifecycle_policy" "service" {
  for_each   = aws_ecr_repository.service
  repository = each.value.name
  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep the 30 most recent images"
      selection    = { tagStatus = "any", countType = "imageCountMoreThan", countNumber = 30 }
      action       = { type = "expire" }
    }]
  })
}

module "service" {
  for_each = local.services
  source   = "./modules/ecs-service"

  name                           = each.key
  region                         = var.region
  cluster_arn                    = aws_ecs_cluster.this.arn
  cluster_name                   = aws_ecs_cluster.this.name
  image                          = "${aws_ecr_repository.service[each.key].repository_url}:${var.image_tag}"
  container_port                 = each.value.port
  cpu                            = each.value.cpu
  memory                         = each.value.memory
  min_capacity                   = each.value.min
  max_capacity                   = each.value.max
  subnet_ids                     = module.network.private_subnet_ids
  security_group_ids             = [aws_security_group.services.id]
  service_discovery_namespace_id = aws_service_discovery_private_dns_namespace.this.id
  target_group_arn               = each.key == "api-gateway" ? module.alb.target_group_arn : null
  log_retention_days             = var.log_retention_days
  alarm_topic_arns               = [module.observability.alarm_topic_arn]

  environment = merge(
    local.common_environment,
    each.value.schema == null ? {} : {
      DB_URL                = "${local.database_url}&currentSchema=${each.value.schema}"
      SPRING_FLYWAY_SCHEMAS = each.value.schema
    },
    each.key == "api-gateway" ? local.gateway_environment : {},
  )

  # Name => Secrets Manager / SSM ARN. The execution role may read exactly these.
  secrets = merge(
    { JWT_SECRET = module.secrets.jwt_secret_arn },
    each.value.schema == null ? {} : {
      DB_USERNAME = "${module.rds.master_user_secret_arn}:username::"
      DB_PASSWORD = "${module.rds.master_user_secret_arn}:password::"
    },
    each.key == "payment-service" ? {
      PAYMENT_PROVIDER = module.secrets.parameter_arns["payment-provider"]
      MOCKPAY_BASE_URL = module.secrets.parameter_arns["mockpay-base-url"]
    } : {},
    each.key == "api-gateway" ? {
      RATE_LIMIT_REPLENISH_RATE = module.secrets.parameter_arns["rate-limit-replenish-rate"]
      RATE_LIMIT_BURST_CAPACITY = module.secrets.parameter_arns["rate-limit-burst-capacity"]
    } : {},
  )
  secret_arns = concat(
    [module.secrets.jwt_secret_arn],
    each.value.schema == null ? [] : [module.rds.master_user_secret_arn],
  )
  ssm_parameter_arns = (
    each.key == "payment-service" ? [module.secrets.parameter_arns["payment-provider"], module.secrets.parameter_arns["mockpay-base-url"]] :
    each.key == "api-gateway" ? [module.secrets.parameter_arns["rate-limit-replenish-rate"], module.secrets.parameter_arns["rate-limit-burst-capacity"]] :
    []
  )

  depends_on = [module.alb]
}

resource "aws_route53_record" "api" {
  zone_id = var.hosted_zone_id
  name    = var.api_domain_name
  type    = "A"

  alias {
    name                   = module.alb.dns_name
    zone_id                = module.alb.zone_id
    evaluate_target_health = true
  }
}
