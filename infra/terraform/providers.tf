provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Project     = "eventflow-commerce"
      Environment = var.environment
      ManagedBy   = "terraform"
    }
  }
}
