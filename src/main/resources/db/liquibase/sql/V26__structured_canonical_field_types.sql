-- CORE-BE-23: typed_value is already JSONB, so structured values require no destructive row rewrite.
ALTER TABLE momna.canonical_field_definitions DROP CONSTRAINT IF EXISTS canonical_field_definitions_data_type_check;
ALTER TABLE momna.canonical_field_definitions ADD CONSTRAINT canonical_field_definitions_data_type_check CHECK (data_type IN ('ENUM','DATE','DATE_TIME','NUMBER','TEXT','BOOLEAN','OBJECT','RECORD','COLLECTION'));
