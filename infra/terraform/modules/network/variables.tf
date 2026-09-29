variable "name" {
  description = "Name prefix for all network resources."
  type        = string
}

variable "cidr_block" {
  description = "VPC CIDR block."
  type        = string
}

variable "az_count" {
  description = "Number of availability zones."
  type        = number
}

variable "single_nat_gateway" {
  description = "Share one NAT gateway across all private subnets."
  type        = bool
}
