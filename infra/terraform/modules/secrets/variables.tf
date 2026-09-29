variable "environment" {
  description = "Environment name, used in secret and parameter paths."
  type        = string
}

variable "mockpay_base_url" {
  description = "Initial value of the mock payment provider URL parameter."
  type        = string
  default     = "http://mockpay.eventflow.internal:8080"
}
