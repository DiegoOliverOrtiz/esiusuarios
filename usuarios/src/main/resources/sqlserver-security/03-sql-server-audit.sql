/*
  ESI Entradas - SQL Server Audit
  Ejecutar como administrador de SQL Server.

  Ajusta FILEPATH a una carpeta existente y protegida del servidor SQL Server.
*/

USE [master];
GO

IF NOT EXISTS (SELECT 1 FROM sys.server_audits WHERE name = N'ESIUsuariosAudit')
BEGIN
    CREATE SERVER AUDIT [ESIUsuariosAudit]
    TO FILE (
        FILEPATH = N'C:\SQLAudit\',
        MAXSIZE = 100 MB,
        MAX_ROLLOVER_FILES = 20,
        RESERVE_DISK_SPACE = OFF
    )
    WITH (
        QUEUE_DELAY = 1000,
        ON_FAILURE = CONTINUE
    );
END
GO

ALTER SERVER AUDIT [ESIUsuariosAudit] WITH (STATE = ON);
GO

USE [esiusuarios];
GO

IF NOT EXISTS (SELECT 1 FROM sys.database_audit_specifications WHERE name = N'ESIUsuariosDatabaseAudit')
BEGIN
    CREATE DATABASE AUDIT SPECIFICATION [ESIUsuariosDatabaseAudit]
    FOR SERVER AUDIT [ESIUsuariosAudit]
        ADD (SELECT, INSERT, UPDATE, DELETE ON OBJECT::dbo.users BY [public]),
        ADD (SELECT, INSERT, UPDATE, DELETE ON OBJECT::dbo.password_reset_tokens BY [public]),
        ADD (DATABASE_PERMISSION_CHANGE_GROUP),
        ADD (SCHEMA_OBJECT_PERMISSION_CHANGE_GROUP);
END
GO

ALTER DATABASE AUDIT SPECIFICATION [ESIUsuariosDatabaseAudit] WITH (STATE = ON);
GO
