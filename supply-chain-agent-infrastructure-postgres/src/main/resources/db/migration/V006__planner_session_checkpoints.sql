-- Durable, optimistic planner-session checkpoints. Search paths are replayed from action IDs;
-- this schema stores only algorithm state and never treats planner output as execution authority.
CREATE TABLE planner_session (
    session_id                          varchar(128) PRIMARY KEY,
    planner_id                          varchar(64) NOT NULL CHECK (planner_id IN ('COT_SEQUENTIAL','TOT_BOUNDED_SEARCH','RAP_INSPIRED_MCTS')),
    request_fingerprint                 char(64) NOT NULL CHECK (request_fingerprint ~ '^[0-9a-f]{64}$'),
    algorithm_configuration_fingerprint varchar(2048) NOT NULL,
    revision                            bigint NOT NULL CHECK (revision >= 1),
    status                              varchar(24) NOT NULL CHECK (status IN ('ACTIVE','COMPLETED')),
    expansions                          bigint NOT NULL CHECK (expansions >= 0),
    evaluations                         bigint NOT NULL CHECK (evaluations >= 0),
    model_calls                         bigint NOT NULL CHECK (model_calls >= 0),
    rollouts                            bigint NOT NULL CHECK (rollouts >= 0),
    pruned_branches                     bigint NOT NULL CHECK (pruned_branches >= 0),
    backtracks                          bigint NOT NULL CHECK (backtracks >= 0),
    cumulative_elapsed                  varchar(128) NOT NULL,
    updated_at                          timestamptz NOT NULL
);
CREATE INDEX ix_planner_session_status_updated ON planner_session(status, updated_at);
CREATE TABLE planner_session_frontier (
    session_id      varchar(128) NOT NULL REFERENCES planner_session(session_id) ON DELETE CASCADE,
    ordinal         integer NOT NULL CHECK (ordinal >= 0),
    node_id         varchar(256) NOT NULL,
    action_ids      text[] NOT NULL,
    score           double precision NOT NULL CHECK (score >= 0 AND score <= 1),
    PRIMARY KEY (session_id, ordinal)
);
CREATE TABLE planner_session_tree_node (
    session_id          varchar(128) NOT NULL REFERENCES planner_session(session_id) ON DELETE CASCADE,
    ordinal             integer NOT NULL CHECK (ordinal >= 0),
    node_id             varchar(256) NOT NULL,
    parent_node_id      varchar(256),
    action_ids          text[] NOT NULL,
    visits              bigint NOT NULL CHECK (visits >= 0),
    total_reward        double precision NOT NULL CHECK (total_reward >= 0),
    expanded_action_ids text[] NOT NULL,
    fully_expanded      boolean NOT NULL,
    PRIMARY KEY (session_id, ordinal),
    UNIQUE (session_id, node_id),
    CHECK (parent_node_id IS NULL OR parent_node_id <> node_id)
);
CREATE INDEX ix_planner_session_tree_parent ON planner_session_tree_node(session_id, parent_node_id);
