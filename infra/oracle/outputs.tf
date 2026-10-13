output "public_ip" {
  value = oci_core_instance.vm.public_ip
}

output "ssh" {
  value = "ssh -i ~/.ssh/oracle_vm ubuntu@${oci_core_instance.vm.public_ip}"
}
