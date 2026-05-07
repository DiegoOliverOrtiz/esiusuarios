$ErrorActionPreference = "Stop"

$apiKey = $env:MAIL_API
$senderAddress = $env:MAIL_USER
$senderName = $env:EMAIL_SENDER_NAME
$frontendUrl = $env:APP_FRONTEND_URL

if ([string]::IsNullOrWhiteSpace($senderName)) {
    $senderName = "ESI Entradas"
}

if ([string]::IsNullOrWhiteSpace($frontendUrl)) {
    $frontendUrl = "http://localhost:4200"
}

if ([string]::IsNullOrWhiteSpace($apiKey) -or $apiKey -eq "dummy") {
    throw "Falta MAIL_API. Configurala una vez con: setx MAIL_API `"xkeysib-tu-clave-real`" y abre una terminal nueva."
}

if ($apiKey.Length -lt 20 -or -not $apiKey.StartsWith("xkeysib-")) {
    throw "MAIL_API no parece una clave valida de Brevo. Debe empezar por xkeysib-."
}

if ([string]::IsNullOrWhiteSpace($senderAddress)) {
    throw "Falta MAIL_USER. Configuralo una vez con: setx MAIL_USER `"correo-verificado-en-brevo@dominio.com`" y abre una terminal nueva."
}

$portLine = netstat -ano | findstr ":8081" | Select-String "LISTENING" | Select-Object -First 1
if ($portLine) {
    $pidText = ($portLine.ToString().Trim() -split "\s+")[-1]
    if ($pidText -match "^\d+$") {
        Write-Host "Parando proceso anterior en puerto 8081: PID $pidText"
        Stop-Process -Id ([int]$pidText) -Force
        Start-Sleep -Seconds 2
    }
}

$env:EMAIL_API_URL = "https://api.brevo.com/v3/smtp/email"
$env:EMAIL_SENDER_NAME = $senderName
$env:FRONTEND_URL = $frontendUrl
$env:APP_FRONTEND_URL = $frontendUrl

Write-Host "MAIL_API cargada desde variable de entorno: si (no se muestra por seguridad)"
Write-Host "MAIL_USER: $senderAddress"
Write-Host "EMAIL_SENDER_NAME: $senderName"
Write-Host "APP_FRONTEND_URL: $frontendUrl"
Write-Host "Arrancando esiusuarios con perfil dev..."

mvn.cmd -Pdev spring-boot:run
