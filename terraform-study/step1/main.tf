//hello.txt라는 파일을 만들고, 그 안에 문자열을 기록하겠다는 선언

terraform {
  // required_providers "이 프로젝트는 local이라는 플러그인이 필요해요"
  required_providers {
    local = {
      source  = "hashicorp/local" //local: 내 컴퓨터의 파일을 관리하는 Provider
      version = "~> 2.5"
    }
  }
}
//local_file: 생성할 리소스의 종류
//hello: 이 리소스에 붙인 Terraform 내부 이름

//resource "local_file" "hello" : "local_file 종류의 자원을 hello라는 이름으로 만들어라"
resource "local_file" "hello" {
  // filename, content             그 자원의 설정값(파일 이름, 내용)
  filename             = "${path.module}/hello.txt"
  content              = "안녕 테라폼\n"
  file_permission      = "0644"
  directory_permission = "0755"
}
