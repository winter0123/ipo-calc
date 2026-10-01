terraform {
  required_providers {
    docker = { source = "kreuzwerker/docker", version = "3.6.2" }
  }
}

provider "docker" {}

variable "db_password" {
  type      = string
  sensitive = true
}

resource "docker_image" "postgres" {
  name = "postgres:16-alpine"
  keep_locally = true # destroy 실행 시 이미지는 로컬에 그대로 유지
}

resource "docker_container" "db" {
  name  = "tf-practice-db"
  image = docker_image.postgres.image_id
  env   = ["POSTGRES_PASSWORD=${var.db_password}"]
  ports {
    internal = 5432
    external = 15433
  }
}

output "db_port" {
  value = docker_container.db.ports[0].external
}
