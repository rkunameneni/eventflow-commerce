variable "name" {
  description = "Identifier prefix."
  type        = string
}

variable "vpc_id" {
  description = "VPC of the database."
  type        = string
}

variable "subnet_ids" {
  description = "Private subnets for the DB subnet group."
  type        = list(string)
}

variable "allowed_security_group_ids" {
  description = "Security groups allowed to connect on 5432."
  type        = list(string)
}

variable "instance_class" {
  description = "RDS instance class."
  type        = string
}

variable "engine_version" {
  description = "Postgres engine version."
  type        = string
  default     = "16.4"
}

variable "database_name" {
  description = "Name of the shared database."
  type        = string
  default     = "eventflow"
}

variable "multi_az" {
  description = "Deploy a standby in a second AZ."
  type        = bool
}

variable "deletion_protection" {
  description = "Protect the instance from deletion and take a final snapshot."
  type        = bool
}
