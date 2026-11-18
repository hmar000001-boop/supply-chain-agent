-- Use ordinal identity for ordered reference collections so repeated references are losslessly retained.
UPDATE coala_working_memory_slot_evidence e
   SET ordinal = ranked.ordinal
  FROM (SELECT ctid, row_number() OVER (PARTITION BY memory_id, slot_key ORDER BY evidence_type, evidence_id) - 1 AS ordinal
          FROM coala_working_memory_slot_evidence) ranked
 WHERE e.ctid = ranked.ctid;
ALTER TABLE coala_working_memory_slot_evidence DROP CONSTRAINT coala_working_memory_slot_evidence_pkey;
ALTER TABLE coala_working_memory_slot_evidence ADD PRIMARY KEY (memory_id, slot_key, ordinal);

UPDATE coala_working_memory_hypothesis_evidence e
   SET ordinal = ranked.ordinal
  FROM (SELECT ctid, row_number() OVER (PARTITION BY memory_id, hypothesis_id, evidence_role ORDER BY evidence_type, evidence_id) - 1 AS ordinal
          FROM coala_working_memory_hypothesis_evidence) ranked
 WHERE e.ctid = ranked.ctid;
ALTER TABLE coala_working_memory_hypothesis_evidence DROP CONSTRAINT coala_working_memory_hypothesis_evidence_pkey;
ALTER TABLE coala_working_memory_hypothesis_evidence ADD PRIMARY KEY (memory_id, hypothesis_id, evidence_role, ordinal);

UPDATE coala_episode_entity e
   SET ordinal = ranked.ordinal
  FROM (SELECT ctid, row_number() OVER (PARTITION BY episode_id ORDER BY entity_type, entity_id) - 1 AS ordinal
          FROM coala_episode_entity) ranked
 WHERE e.ctid = ranked.ctid;
ALTER TABLE coala_episode_entity DROP CONSTRAINT coala_episode_entity_pkey;
ALTER TABLE coala_episode_entity ADD PRIMARY KEY (episode_id, ordinal);

UPDATE coala_episode_event_evidence e
   SET ordinal = ranked.ordinal
  FROM (SELECT ctid, row_number() OVER (PARTITION BY episode_id, event_index ORDER BY evidence_type, evidence_id) - 1 AS ordinal
          FROM coala_episode_event_evidence) ranked
 WHERE e.ctid = ranked.ctid;
ALTER TABLE coala_episode_event_evidence DROP CONSTRAINT coala_episode_event_evidence_pkey;
ALTER TABLE coala_episode_event_evidence ADD PRIMARY KEY (episode_id, event_index, ordinal);
