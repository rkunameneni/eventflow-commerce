resource "aws_security_group" "msk" {
  name        = "${var.name}-msk"
  description = "Kafka brokers, reachable from the service tasks only"
  vpc_id      = var.vpc_id

  ingress {
    description     = "Kafka plaintext (in-VPC) and TLS listeners"
    from_port       = 9092
    to_port         = 9094
    protocol        = "tcp"
    security_groups = var.allowed_security_group_ids
  }
}

resource "aws_cloudwatch_log_group" "brokers" {
  name              = "/msk/${var.name}"
  retention_in_days = var.log_retention_days
}

resource "aws_msk_configuration" "this" {
  name           = "${var.name}-config"
  kafka_versions = [var.kafka_version]

  # Topics are declared by the services (with explicit partitions and replication), so
  # auto-creation is off: a typo in a topic name fails loudly instead of creating a new topic.
  server_properties = <<-EOT
    auto.create.topics.enable=false
    default.replication.factor=${min(var.broker_count, 3)}
    min.insync.replicas=${min(var.broker_count, 3) - 1}
    unclean.leader.election.enable=false
  EOT
}

resource "aws_msk_cluster" "this" {
  cluster_name           = var.name
  kafka_version          = var.kafka_version
  number_of_broker_nodes = var.broker_count

  broker_node_group_info {
    instance_type   = var.instance_type
    client_subnets  = var.subnet_ids
    security_groups = [aws_security_group.msk.id]

    storage_info {
      ebs_storage_info {
        volume_size = var.volume_size
      }
    }
  }

  configuration_info {
    arn      = aws_msk_configuration.this.arn
    revision = aws_msk_configuration.this.latest_revision
  }

  encryption_info {
    encryption_in_transit {
      # TLS between brokers; clients inside the VPC may use plaintext or TLS.
      client_broker = "TLS_PLAINTEXT"
      in_cluster    = true
    }
  }

  logging_info {
    broker_logs {
      cloudwatch_logs {
        enabled   = true
        log_group = aws_cloudwatch_log_group.brokers.name
      }
    }
  }
}
