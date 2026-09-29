variable "name" {
  description = "Service name; also the container name and Cloud Map record."
  type        = string
}

variable "region" {
  description = "AWS region (for the awslogs driver)."
  type        = string
}

variable "cluster_arn" {
  description = "ECS cluster ARN."
  type        = string
}

variable "cluster_name" {
  description = "ECS cluster name (for auto-scaling and alarms)."
  type        = string
}

variable "image" {
  description = "Container image including tag."
  type        = string
}

variable "container_port" {
  description = "Port the application listens on."
  type        = number
}

variable "cpu" {
  description = "Fargate task CPU units."
  type        = number
}

variable "memory" {
  description = "Fargate task memory (MiB)."
  type        = number
}

variable "min_capacity" {
  description = "Minimum number of tasks."
  type        = number
}

variable "max_capacity" {
  description = "Maximum number of tasks."
  type        = number
}

variable "cpu_target_utilization" {
  description = "Average CPU the target-tracking policy aims for."
  type        = number
  default     = 60
}

variable "subnet_ids" {
  description = "Private subnets for the tasks."
  type        = list(string)
}

variable "security_group_ids" {
  description = "Security groups attached to the tasks."
  type        = list(string)
}

variable "service_discovery_namespace_id" {
  description = "Cloud Map namespace for service-to-service DNS."
  type        = string
}

variable "target_group_arn" {
  description = "ALB target group to register with; null for internal services."
  type        = string
  default     = null
}

variable "environment" {
  description = "Plain environment variables."
  type        = map(string)
  default     = {}
}

variable "secrets" {
  description = "Environment variables injected from Secrets Manager or SSM (name => valueFrom)."
  type        = map(string)
  default     = {}
}

variable "secret_arns" {
  description = "Secrets Manager secrets the execution role may read."
  type        = list(string)
  default     = []
}

variable "ssm_parameter_arns" {
  description = "SSM parameters the execution role may read."
  type        = list(string)
  default     = []
}

variable "enable_xray" {
  description = "Run the AWS Distro for OpenTelemetry collector sidecar that exports traces to X-Ray."
  type        = bool
  default     = true
}

variable "otel_collector_image" {
  description = "Image of the ADOT collector sidecar."
  type        = string
  default     = "public.ecr.aws/aws-observability/aws-otel-collector:v0.40.0"
}

variable "log_retention_days" {
  description = "CloudWatch Logs retention."
  type        = number
}

variable "alarm_topic_arns" {
  description = "SNS topics notified by this service's alarms."
  type        = list(string)
  default     = []
}
