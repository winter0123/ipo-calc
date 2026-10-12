variable "tenancy_ocid" { type = string }
variable "user_ocid" { type = string }
variable "fingerprint" { type = string }
variable "private_key_path" { type = string }
variable "region" { type = string }

variable "ssh_public_key_path" {
  type        = string
  description = "VM 접속용 SSH 공개키 파일 경로"
}

variable "ssh_allowed_cidr" {
  type        = string
  default     = "0.0.0.0/0"
  description = "SSH(22) 허용 대역. 내 공인 IP/32 로 좁히는 것을 권장"
}

# Always Free 한도(Oracle 공식 문서 확인): A1 합계 2 OCPU / 12GB. 용량 부족 시 1 OCPU / 6GB로 줄여서 재시도한다.
variable "ocpus" {
  type    = number
  default = 2
}
variable "memory_gbs" {
  type    = number
  default = 12
}
variable "availability_domain_index" {
  type    = number
  default = 0
}
