Vagrant.configure(2) do |config|
  config.vm.box = "ubuntu/vivid64"

  config.vm.provision "shell", inline: <<-SHELL
    sudo apt-get update
    sudo apt-get install -y docker.io
    sudo service docker start
    sudo adduser vagrant docker
  SHELL
end
