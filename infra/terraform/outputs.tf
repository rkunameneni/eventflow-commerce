output "api_url" {
  description = "Public base URL of the API."
  value       = "https://${var.api_domain_name}"
}

output "alb_dns_name" {
  description = "DNS name of the application load balancer."
  value       = module.alb.dns_name
}

output "ecs_cluster_name" {
  description = "ECS cluster running the services."
  value       = aws_ecs_cluster.this.name
}

output "ecr_repository_urls" {
  description = "ECR repository per service; CI pushes images here."
  value       = { for name, repo in aws_ecr_repository.service : name => repo.repository_url }
}

output "database_endpoint" {
  description = "RDS endpoint (credentials are in the RDS-managed secret)."
  value       = "${module.rds.address}:${module.rds.port}"
}

output "redis_endpoint" {
  description = "ElastiCache primary endpoint."
  value       = module.redis.primary_endpoint
}

output "kafka_bootstrap_servers" {
  description = "Kafka bootstrap servers used by the services."
  value       = local.kafka_bootstrap_servers
}

output "alarm_topic_arn" {
  description = "SNS topic that receives CloudWatch alarms."
  value       = module.observability.alarm_topic_arn
}
