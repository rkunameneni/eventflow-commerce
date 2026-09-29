resource "aws_db_subnet_group" "this" {
  name       = var.name
  subnet_ids = var.subnet_ids
}

resource "aws_security_group" "db" {
  name        = "${var.name}-postgres"
  description = "Postgres, reachable from the service tasks only"
  vpc_id      = var.vpc_id

  ingress {
    description     = "Postgres from services"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = var.allowed_security_group_ids
  }
}

resource "aws_db_parameter_group" "this" {
  name   = "${var.name}-postgres16"
  family = "postgres16"

  parameter {
    name  = "rds.force_ssl"
    value = "1"
  }

  parameter {
    name  = "log_min_duration_statement"
    value = "500"
  }
}

# One instance and one database; each service owns a schema in it (see docs/adr).
resource "aws_db_instance" "this" {
  identifier     = var.name
  engine         = "postgres"
  engine_version = var.engine_version
  instance_class = var.instance_class

  db_name  = var.database_name
  username = "eventflow"
  # RDS generates and rotates the password in Secrets Manager; it never appears in state or code.
  manage_master_user_password = true

  allocated_storage     = 20
  max_allocated_storage = 200
  storage_type          = "gp3"
  storage_encrypted     = true

  multi_az               = var.multi_az
  db_subnet_group_name   = aws_db_subnet_group.this.name
  vpc_security_group_ids = [aws_security_group.db.id]
  parameter_group_name   = aws_db_parameter_group.this.name
  publicly_accessible    = false

  backup_retention_period      = 7
  copy_tags_to_snapshot        = true
  auto_minor_version_upgrade   = true
  performance_insights_enabled = true
  deletion_protection          = var.deletion_protection
  skip_final_snapshot          = !var.deletion_protection
  final_snapshot_identifier    = var.deletion_protection ? "${var.name}-final" : null
}
