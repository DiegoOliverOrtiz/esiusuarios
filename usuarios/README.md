# esiusuarios

Backend Spring Boot para la gestion de cuentas de usuario.

## Configuracion Local De SQL Server

La base de datos y el login SQL no forman parte del codigo. Cada maquina debe
tener creada la base `esiusuarios` y un login de aplicacion, o debe indicar sus
propias credenciales por variables de entorno.

Para desarrollo:

```powershell
Copy-Item .env.example .env
.\start-dev.ps1
```

Valores por defecto de desarrollo:

```properties
SQLSERVER_HOST=localhost
SQLSERVER_PORT=1433
SQLSERVER_DATABASE=esiusuarios
SQLSERVER_USER=usuarios_app
SQLSERVER_PASSWORD=UsuariosApp!2026
```

Si SQL Server se levanta con el `docker-compose.yml` de `Backend`, cambia
`SQLSERVER_PORT` a `14333` en tu `.env`.

## Crear La Base Local

En SQL Server Management Studio, con un usuario administrador, ejecuta:

```sql
Backend/mssql-init/local-sqlserver-init.sql
```

Ese script crea la base `esiusuarios`, el login `usuarios_app` y los permisos
minimos que necesita la aplicacion.

## Arranque

Arranque recomendado en desarrollo:

```powershell
.\start-dev.ps1
```

Alternativa equivalente:

```powershell
mvn.cmd -Pdev spring-boot:run
```

Para ejecutar sin perfil `dev`, define obligatoriamente `SQLSERVER_PASSWORD`.
Esto evita que la aplicacion intente conectarse con una contrasena vacia.
