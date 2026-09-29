terraform {
  required_version = ">= 1.6.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.70"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }

  # Partial configuration: supply bucket/key/region/dynamodb_table with
  #   terraform init -backend-config=backend.hcl
  backend "s3" {}
}
