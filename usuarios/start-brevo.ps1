$ErrorActionPreference = "Stop"

$apiKey = $env:MAIL_API
if ([string]::IsNullOrWhiteSpace($apiKey)) {
    $apiKey = Read-Host "Pega tu API key de Brevo completa, debe empezar por xkeysib-"
}

$senderAddress = $env:MAIL_USER
if ([string]::IsNullOrWhiteSpace($senderAddress)) {
    $senderAddress = Read-Host "Correo remitente verificado en Brevo"
}

$senderName = Read-Host "Nombre del remitente [ESI Entradas]"
$frontendUrl = Read-Host "URL del frontend [http://localhost:4200]"

if ([string]::IsNullOrWhiteSpace($senderName)) {
    $senderName = "ESI Entradas"
}

if ([string]::IsNullOrWhiteSpace($frontendUrl)) {
    $frontendUrl = $env:APP_FRONTEND_URL
}

if ([string]::IsNullOrWhiteSpace($frontendUrl)) {
    $frontendUrl = "http://localhost:4200"
}

$apiKey = $apiKey.Trim()
$senderAddress = $senderAddress.Trim()
$senderName = $senderName.Trim()
$frontendUrl = $frontendUrl.Trim()

if ([string]::IsNullOrWhiteSpace($apiKey)) {
    throw "La API key de Brevo es obligatoria."
}

if ($apiKey.Length -lt 20 -or -not $apiKey.StartsWith("xkeysib-")) {
    throw "La API key cargada no parece valida. Debe ser la clave completa de Brevo y empezar por xkeysib-."
}

if ([string]::IsNullOrWhiteSpace($senderAddress)) {
    throw "El correo remitente verificado en Brevo es obligatorio."
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
$env:EMAIL_API_KEY = $apiKey
$env:MAIL_API = $apiKey
$env:EMAIL_SENDER_NAME = $senderName
$env:EMAIL_SENDER_ADDRESS = $senderAddress
$env:MAIL_USER = $senderAddress
$env:FRONTEND_URL = $frontendUrl
$env:APP_FRONTEND_URL = $frontendUrl

Write-Host "API key cargada: si (longitud $($apiKey.Length), no se muestra por seguridad)"
Write-Host "Remitente: $senderAddress"
Write-Host "Frontend: $frontendUrl"
Write-Host "Arrancando esiusuarios con Brevo configurado..."
mvn.cmd spring-boot:run
