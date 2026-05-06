# Controles De Seguridad SQL Server

Estos scripts se ejecutan manualmente con un usuario administrador de SQL Server.
La aplicacion no debe tener permisos para crear auditorias, logins ni claves.

## 1. Menor Privilegio

Ejecuta `01-least-privilege.sql`.

La aplicacion usa el usuario `usuarios_app`, no `sa`. En produccion configura:

```powershell
$env:SQLSERVER_USER="usuarios_app"
$env:SQLSERVER_PASSWORD="<valor-secreto-en-variable-de-entorno>"
```

No concedas `db_owner`, `sysadmin`, `ALTER`, `CONTROL` ni `DELETE` si no es necesario.

## 2. Dynamic Data Masking

Ejecuta `02-dynamic-data-masking.sql`.

Sirve para que una consulta directa vea datos parcialmente ocultos. No sustituye
al cifrado AES-256-GCM de la aplicacion.

## 3. SQL Server Audit

Ejecuta `03-sql-server-audit.sql`.

Antes crea la carpeta del audit en el servidor, por ejemplo:

```powershell
New-Item -ItemType Directory -Path C:\SQLAudit
```

Registra accesos y cambios sobre `users` y `password_reset_tokens`.

## 4. Always Encrypted

`04-always-encrypted-template.sql` es una plantilla. Always Encrypted requiere
un certificado real o Azure Key Vault, y SSMS debe generar la CEK cifrada.

La aplicacion ya lleva:

```properties
columnEncryptionSetting=Enabled
```

en la URL JDBC de SQL Server.

## 5. Relacion Con AES-256-GCM De La Aplicacion

Las contrasenas se guardan con hash BCrypt porque no deben recuperarse.

Los datos de riesgo como DNI/NIE, telefono y direccion se cifran con AES-256-GCM
porque pueden necesitar recuperarse para mostrarse al usuario. La clave se lee
desde `RISK_DATA_ENCRYPTION_KEY`, no desde la base de datos.
