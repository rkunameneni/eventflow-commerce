terraform {
  required_providers {
    aws = {
      source = "hashicorp/aws"
    }
    random = {
      source = "hashicorp/random"
    }
  }
}

# JWT signing key: generated here, stored only in Secrets Manager, injected into tasks by ECS.
resource "random_password" "jwt_signing_key" {
  length  = 64
  special = false
}

resource "aws_secretsmanager_secret" "jwt_signing_key" {
  name                    = "eventflow/${var.environment}/jwt-signing-key"
  description             = "HMAC key used to sign and verify EventFlow access tokens"
  recovery_window_in_days = 7
}

resource "aws_secretsmanager_secret_version" "jwt_signing_key" {
  secret_id     = aws_secretsmanager_secret.jwt_signing_key.id
  secret_string = random_password.jwt_signing_key.result
}

# Non-secret runtime settings in SSM Parameter Store: operators can change them without a
# Terraform run (ECS reads them when a task starts).
locals {
  parameters = {
    "payment-provider"          = { value = "mockpay", description = "Active payment provider strategy" }
    "mockpay-base-url"          = { value = var.mockpay_base_url, description = "Base URL of the external mock payment provider" }
    "rate-limit-replenish-rate" = { value = "20", description = "Gateway token-bucket refill rate per user (req/s)" }
    "rate-limit-burst-capacity" = { value = "40", description = "Gateway token-bucket size per user" }
  }
}

resource "aws_ssm_parameter" "setting" {
  for_each    = local.parameters
  name        = "/eventflow/${var.environment}/${each.key}"
  description = each.value.description
  type        = "String"
  value       = each.value.value

  lifecycle {
    ignore_changes = [value]
  }
}
