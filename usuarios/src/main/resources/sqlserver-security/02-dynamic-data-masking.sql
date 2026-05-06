/*
  ESI Entradas - Dynamic Data Masking
  Ejecutar en la base esiusuarios con permisos de ALTER sobre las tablas.

  Nota:
  Los datos de riesgo ya se guardan cifrados por la aplicacion con AES-256-GCM.
  El enmascaramiento aqui es defensa adicional frente a consultas directas.
  No concedas UNMASK al usuario de aplicacion.
*/

USE [esiusuarios];
GO

IF COL_LENGTH('dbo.users', 'email') IS NOT NULL
BEGIN
    ALTER TABLE dbo.users
    ALTER COLUMN email varchar(180) MASKED WITH (FUNCTION = 'email()') NOT NULL;
END
GO

IF COL_LENGTH('dbo.users', 'dni_nie_encrypted') IS NOT NULL
BEGIN
    ALTER TABLE dbo.users
    ALTER COLUMN dni_nie_encrypted varchar(512) MASKED WITH (FUNCTION = 'partial(0,"XXXX-",4)');
END
GO

IF COL_LENGTH('dbo.users', 'telefono_encrypted') IS NOT NULL
BEGIN
    ALTER TABLE dbo.users
    ALTER COLUMN telefono_encrypted varchar(512) MASKED WITH (FUNCTION = 'partial(0,"XXXXX-",4)');
END
GO

IF COL_LENGTH('dbo.users', 'direccion_encrypted') IS NOT NULL
BEGIN
    ALTER TABLE dbo.users
    ALTER COLUMN direccion_encrypted varchar(1024) MASKED WITH (FUNCTION = 'default()');
END
GO

DENY UNMASK TO [usuarios_app];
GO
