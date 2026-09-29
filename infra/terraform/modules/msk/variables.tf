variable "name" {
  description = "Cluster name."
  type        = string
}

variable "vpc_id" {
  description = "VPC of the cluster."
  type        = string
}

variable "subnet_ids" {
  description = "Private subnets, one broker group per subnet."
  type        = list(string)
}

variable "allowed_security_group_ids" {
  description = "Security groups allowed to reach the brokers."
  type        = list(string)
}

variable "kafka_version" {
  description = "Apache Kafka version."
  type        = string
  default     = "3.6.0"
}

variable "broker_count" {
  description = "Number of brokers (multiple of the number of subnets)."
  type        = number
}

variable "instance_type" {
  description = "Broker instance type."
  type        = string
}

variable "volume_size" {
  description = "EBS storage per broker in GiB."
  type        = number
  default     = 50
}

variable "log_retention_days" {
  description = "Retention of broker logs in CloudWatch."
  type        = number
}
