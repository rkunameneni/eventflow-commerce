output "bootstrap_brokers" {
  description = "Plaintext bootstrap servers (in-VPC)."
  value       = aws_msk_cluster.this.bootstrap_brokers
}

output "bootstrap_brokers_tls" {
  description = "TLS bootstrap servers."
  value       = aws_msk_cluster.this.bootstrap_brokers_tls
}
