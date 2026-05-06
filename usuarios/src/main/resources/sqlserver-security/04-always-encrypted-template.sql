/*
  ESI Entradas - Always Encrypted template

  Importante:
  Always Encrypted no se puede activar correctamente con un script generico
  completo porque la clave de cifrado de columna (CEK) debe generarse/cifrarse
  con el certificado o Key Vault real de tu entorno.

  En este proyecto los datos de riesgo se cifran en la aplicacion con AES-256-GCM.
  Si ademas quieres activar Always Encrypted en SQL Server, usa este guion:

  1. Crea o selecciona un certificado en Windows Certificate Store o una clave
     en Azure Key Vault.
  2. En SSMS:
     Database -> Security -> Always Encrypted Keys
     - Create Column Master Key
     - Create Column Encryption Key
  3. Activa en la conexion JDBC:
     columnEncryptionSetting=Enabled
     Ya esta configurado en application.properties.
  4. Convierte las columnas con el asistente:
     Tasks -> Encrypt Columns...
     Columnas recomendadas si decides usar Always Encrypted ademas del AES de app:
     - dbo.users.dni_nie_encrypted
     - dbo.users.telefono_encrypted
     - dbo.users.direccion_encrypted

  Plantilla conceptual. Sustituye KEY_PATH y ENCRYPTED_VALUE por valores reales
  generados por SSMS o por tu Key Vault.
*/

USE [esiusuarios];
GO

/*
CREATE COLUMN MASTER KEY [CMK_ESIUsuarios]
WITH (
    KEY_STORE_PROVIDER_NAME = N'MSSQL_CERTIFICATE_STORE',
    KEY_PATH = N'CurrentUser/My/THUMBPRINT_DEL_CERTIFICADO'
);
GO

CREATE COLUMN ENCRYPTION KEY [CEK_ESIUsuarios]
WITH VALUES (
    COLUMN_MASTER_KEY = [CMK_ESIUsuarios],
    ALGORITHM = 'RSA_OAEP',
    ENCRYPTED_VALUE = 0xVALOR_GENERADO_POR_SSMS
);
GO

-- Ejemplo conceptual para una columna nueva.
-- Para columnas existentes, usa el asistente "Encrypt Columns" de SSMS,
-- porque debe migrar datos y metadatos de forma segura.
ALTER TABLE dbo.users
ALTER COLUMN telefono_encrypted varchar(512)
COLLATE Latin1_General_BIN2
ENCRYPTED WITH (
    COLUMN_ENCRYPTION_KEY = [CEK_ESIUsuarios],
    ENCRYPTION_TYPE = Randomized,
    ALGORITHM = 'AEAD_AES_256_CBC_HMAC_SHA_256'
);
GO
*/
