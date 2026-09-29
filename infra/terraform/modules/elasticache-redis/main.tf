resource "aws_elasticache_subnet_group" "this" {
  name       = var.name
  subnet_ids = var.subnet_ids
}

resource "aws_security_group" "redis" {
  name        = "${var.name}-redis"
  description = "Redis, reachable from the service tasks only"
  vpc_id      = var.vpc_id

  ingress {
    description     = "Redis from services"
    from_port       = 6379
    to_port         = 6379
    protocol        = "tcp"
    security_groups = var.allowed_security_group_ids
  }
}

# Primary + replica with automatic failover. Holds only rebuildable data (cache entries and
# rate-limit buckets), so no snapshots are kept.
resource "aws_elasticache_replication_group" "this" {
  replication_group_id       = var.name
  description                = "EventFlow cache and rate-limit store"
  engine                     = "redis"
  engine_version             = "7.1"
  node_type                  = var.node_type
  port                       = 6379
  parameter_group_name       = "default.redis7"
  num_cache_clusters         = 2
  automatic_failover_enabled = true
  multi_az_enabled           = true
  at_rest_encryption_enabled = true
  transit_encryption_enabled = true
  subnet_group_name          = aws_elasticache_subnet_group.this.name
  security_group_ids         = [aws_security_group.redis.id]
  snapshot_retention_limit   = 0
  apply_immediately          = false
}
