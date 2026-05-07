$port = 8081
$connections = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue

foreach ($connection in $connections) {
    Write-Host "Parando proceso $($connection.OwningProcess) que ocupa el puerto $port..."
    Stop-Process -Id $connection.OwningProcess -Force
}

Write-Host "Arrancando esiusuarios en http://localhost:$port con perfil dev..."
mvn.cmd -Pdev spring-boot:run
