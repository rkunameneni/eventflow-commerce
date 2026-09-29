variable "region" {
  description = "AWS region to deploy into."
  type        = string
  default     = "us-east-1"
}

variable "environment" {
  description = "Environment name, used in resource names (e.g. dev, staging, prod)."
  type        = string

  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{1,15}$", var.environment))
    error_message = "environment must be 2-16 lower-case letters, digits or dashes."
  }
}

variable "image_tag" {
  description = "Container image tag deployed for every service (CI publishes one tag per commit)."
  type        = string
}

variable "api_domain_name" {
  description = "Public DNS name of the API, e.g. api.eventflow.example.com."
  type        = string
}

variable "hosted_zone_id" {
  description = "Route 53 hosted zone that contains api_domain_name."
  type        = string
}

variable "certificate_arn" {
  description = "ACM certificate for api_domain_name, attached to the HTTPS listener."
  type        = string
}

variable "vpc_cidr" {
  description = "CIDR block of the VPC."
  type        = string
  default     = "10.40.0.0/16"
}

variable "az_count" {
  description = "Number of availability zones to spread subnets across."
  type        = number
  default     = 2
}

variable "single_nat_gateway" {
  description = "Use one shared NAT gateway (cheaper) instead of one per AZ (more resilient)."
  type        = bool
  default     = true
}

variable "db_instance_class" {
  description = "RDS instance class."
  type        = string
  default     = "db.t4g.medium"
}

variable "db_multi_az" {
  description = "Run RDS with a synchronous standby in a second AZ."
  type        = bool
  default     = false
}

variable "redis_node_type" {
  description = "ElastiCache node type."
  type        = string
  default     = "cache.t4g.small"
}

variable "create_msk" {
  description = "Provision Amazon MSK. Set to false to use an existing Kafka cluster instead."
  type        = bool
  default     = true
}

variable "msk_broker_count" {
  description = "Number of MSK brokers; must be a multiple of az_count."
  type        = number
  default     = 2
}

variable "msk_instance_type" {
  description = "MSK broker instance type."
  type        = string
  default     = "kafka.t3.small"
}

variable "external_kafka_bootstrap_servers" {
  description = "Bootstrap servers of an existing Kafka cluster, used when create_msk is false."
  type        = string
  default     = ""
}

variable "alarm_email" {
  description = "Optional e-mail address subscribed to the alarm topic."
  type        = string
  default     = ""
}

variable "log_retention_days" {
  description = "CloudWatch Logs retention for service logs."
  type        = number
  default     = 30
}

variable "deletion_protection" {
  description = "Protect the load balancer and database from accidental deletion."
  type        = bool
  default     = true
}
