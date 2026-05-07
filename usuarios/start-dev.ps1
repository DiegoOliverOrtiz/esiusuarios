$port = 8081

$envFile = Join-Path $PSScriptRoot ".env"
if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        $line = $_.Trim()
        if ($line -eq "" -or $line.StartsWith("#") -or -not $line.Contains("=")) {
            return
        }

        $key, $value = $line.Split("=", 2)
        $key = $key.Trim()
        $value = $value.Trim().Trim('"').Trim("'")
        if ($key) {
            [Environment]::SetEnvironmentVariable($key, $value, "Process")
        }
    }
    Write-Host "Cargadas variables locales desde $envFile"
}

$connections = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue

foreach ($connection in $connections) {
    Write-Host "Parando proceso $($connection.OwningProcess) que ocupa el puerto $port..."
    Stop-Process -Id $connection.OwningProcess -Force
}

Write-Host "Arrancando esiusuarios en http://localhost:$port con perfil dev..."
mvn.cmd -Pdev spring-boot:run
