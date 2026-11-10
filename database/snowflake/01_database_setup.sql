-- =====================================================================
-- TransitPulse - Snowflake warehouse setup
--
-- Creates the warehouse, database, schemas, file format and internal
-- stage used to load the Python engine's output.
--
-- Credentials are NEVER stored here. Authenticate with SnowSQL using
-- environment variables or a connections.toml profile:
--   SNOWSQL_ACCOUNT, SNOWSQL_USER, SNOWSQL_PWD / private key, SNOWSQL_ROLE
-- =====================================================================

-- Compute. XSMALL with auto-suspend keeps demo cost near zero.
CREATE WAREHOUSE IF NOT EXISTS TRANSITPULSE_WH
  WAREHOUSE_SIZE   = 'XSMALL'
  AUTO_SUSPEND     = 60
  AUTO_RESUME      = TRUE
  INITIALLY_SUSPENDED = TRUE
  COMMENT = 'Compute for TransitPulse analytics';

CREATE DATABASE IF NOT EXISTS TRANSITPULSE
  COMMENT = 'Public transit delay propagation and passenger impact analysis';

USE WAREHOUSE TRANSITPULSE_WH;
USE DATABASE TRANSITPULSE;

-- RAW holds files as loaded; CORE holds the star schema; ANALYTICS holds
-- the Power BI facing views. Separating them keeps reload safe.
CREATE SCHEMA IF NOT EXISTS RAW       COMMENT = 'Landing zone, as-loaded data';
CREATE SCHEMA IF NOT EXISTS CORE      COMMENT = 'Conformed dimensions and facts';
CREATE SCHEMA IF NOT EXISTS ANALYTICS COMMENT = 'Power BI ready views';

CREATE FILE FORMAT IF NOT EXISTS RAW.CSV_FORMAT
  TYPE                         = 'CSV'
  FIELD_DELIMITER              = ','
  SKIP_HEADER                  = 1
  FIELD_OPTIONALLY_ENCLOSED_BY = '"'
  -- a blank actual arrival must land as NULL, never as a zero or a string
  NULL_IF                      = ('', 'NULL', 'null', 'NaN', 'nan', 'None')
  EMPTY_FIELD_AS_NULL          = TRUE
  TRIM_SPACE                   = TRUE
  ERROR_ON_COLUMN_COUNT_MISMATCH = TRUE
  COMMENT = 'Format of the datasets and engine output CSV files';

-- Internal stage for local loading via SnowSQL PUT.
CREATE STAGE IF NOT EXISTS RAW.TRANSITPULSE_STAGE
  FILE_FORMAT = RAW.CSV_FORMAT
  COMMENT = 'Internal stage for TransitPulse CSV loads';

-- OPTIONAL: load directly from S3 instead of PUT. Create the storage
-- integration with an IAM role; never embed keys in SQL.
--
-- CREATE STORAGE INTEGRATION TRANSITPULSE_S3_INT
--   TYPE = EXTERNAL_STAGE
--   STORAGE_PROVIDER = 'S3'
--   STORAGE_AWS_ROLE_ARN = '<arn-of-your-iam-role>'
--   ENABLED = TRUE
--   STORAGE_ALLOWED_LOCATIONS = ('s3://<your-bucket>/transitpulse/');
--
-- CREATE STAGE RAW.TRANSITPULSE_S3_STAGE
--   STORAGE_INTEGRATION = TRANSITPULSE_S3_INT
--   URL = 's3://<your-bucket>/transitpulse/processed/'
--   FILE_FORMAT = RAW.CSV_FORMAT;

SHOW SCHEMAS IN DATABASE TRANSITPULSE;
