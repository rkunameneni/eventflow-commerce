output "jwt_secret_arn" {
  value = aws_secretsmanager_secret.jwt_signing_key.arn
}

output "parameter_arns" {
  description = "SSM parameter ARNs keyed by short name."
  value       = { for key, parameter in aws_ssm_parameter.setting : key => parameter.arn }
}
