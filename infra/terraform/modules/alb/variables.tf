variable "name" {
  description = "Name prefix."
  type        = string
}

variable "vpc_id" {
  description = "VPC of the load balancer."
  type        = string
}

variable "public_subnet_ids" {
  description = "Public subnets for the load balancer."
  type        = list(string)
}

variable "certificate_arn" {
  description = "ACM certificate for the HTTPS listener."
  type        = string
}

variable "target_port" {
  description = "Container port of the API gateway."
  type        = number
}

variable "health_check_path" {
  description = "Target-group health check path."
  type        = string
}

variable "deletion_protection" {
  description = "Enable deletion protection on the load balancer."
  type        = bool
}
