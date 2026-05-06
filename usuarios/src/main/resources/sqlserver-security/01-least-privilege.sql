/*
  ESI Entradas - SQL Server security baseline
  Ejecutar con un usuario administrador de SQL Server, no desde la aplicacion.

  Objetivo:
  - Crear un login/usuario especifico para la aplicacion.
  - Evitar que la aplicacion conecte como sa o db_owner.
  - Dar solo permisos sobre las tablas que necesita esiusuarios.

  Antes de ejecutar:
  - Cambia la contrasena de ejemplo.
  - Si las tablas aun no existen, arranca la aplicacion una vez con un usuario
    de migracion o crea el esquema antes de aplicar permisos.
*/

USE [master];
GO

IF NOT EXISTS (SELECT 1 FROM sys.sql_logins WHERE name = N'usuarios_app')
BEGIN
    CREATE LOGIN [usuarios_app]
    WITH PASSWORD = N'CAMBIA_ESTA_PASSWORD_LARGA_123!',
         CHECK_POLICY = ON,
         CHECK_EXPIRATION = ON;
END
GO

USE [esiusuarios];
GO

IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name = N'usuarios_app')
BEGIN
    CREATE USER [usuarios_app] FOR LOGIN [usuarios_app];
END
GO

DENY DELETE TO [usuarios_app];
DENY ALTER TO [usuarios_app];
DENY CONTROL TO [usuarios_app];
DENY TAKE OWNERSHIP TO [usuarios_app];
GO

GRANT SELECT, INSERT, UPDATE ON OBJECT::dbo.users TO [usuarios_app];
GRANT SELECT, INSERT, UPDATE ON OBJECT::dbo.password_reset_tokens TO [usuarios_app];
GO

/*
  Si usas tablas adicionales para compra/entradas en esta misma base de datos,
  concede permisos solo sobre esas tablas concretas, no sobre toda la base.
*/
