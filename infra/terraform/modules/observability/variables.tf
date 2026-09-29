variable "name" {
  description = "Name prefix."
  type        = string
}

variable "alarm_email" {
  description = "Optional e-mail subscriber for alarms."
  type        = string
  default     = ""
}

variable "alb_arn_suffix" {
  description = "ARN suffix of the load balancer (CloudWatch dimension)."
  type        = string
}

variable "target_group_arn_suffix" {
  description = "ARN suffix of the gateway target group (CloudWatch dimension)."
  type        = string
}

variable "db_instance_identifier" {
  description = "RDS instance identifier (CloudWatch dimension)."
  type        = string
}
