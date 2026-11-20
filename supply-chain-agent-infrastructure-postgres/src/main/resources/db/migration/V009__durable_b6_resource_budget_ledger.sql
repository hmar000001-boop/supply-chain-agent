-- M8-B4: durable B6 computational-resource budget accounts and reservations.
-- M7 migrations V001-V008 remain immutable; this is an additive M8 reliability capability.

CREATE TABLE b6_resource_budget (
    scope_id       varchar(256) NOT NULL,
    execution_id   varchar(256) NOT NULL,
    deadline       timestamptz NOT NULL,
    created_at     timestamptz NOT NULL,
    updated_at     timestamptz NOT NULL,
    PRIMARY KEY (scope_id, execution_id),
    CHECK (updated_at >= created_at)
);
CREATE INDEX b6_resource_budget_updated_idx ON b6_resource_budget(scope_id, updated_at DESC);

CREATE TABLE b6_resource_budget_limit (
    scope_id       varchar(256) NOT NULL,
    execution_id   varchar(256) NOT NULL,
    unit           varchar(64) NOT NULL,
    limit_value    bigint NOT NULL CHECK (limit_value >= 0),
    PRIMARY KEY (scope_id, execution_id, unit),
    FOREIGN KEY (scope_id, execution_id) REFERENCES b6_resource_budget(scope_id, execution_id) ON DELETE RESTRICT
);

CREATE TABLE b6_resource_budget_consumption (
    scope_id       varchar(256) NOT NULL,
    execution_id   varchar(256) NOT NULL,
    unit           varchar(64) NOT NULL,
    consumed_value bigint NOT NULL CHECK (consumed_value >= 0),
    PRIMARY KEY (scope_id, execution_id, unit),
    FOREIGN KEY (scope_id, execution_id) REFERENCES b6_resource_budget(scope_id, execution_id) ON DELETE RESTRICT
);

CREATE TABLE b6_resource_budget_reservation (
    scope_id       varchar(256) NOT NULL,
    execution_id   varchar(256) NOT NULL,
    reservation_id varchar(256) NOT NULL,
    status         varchar(16) NOT NULL CHECK (status IN ('ACTIVE','COMMITTED','RELEASED')),
    created_at     timestamptz NOT NULL,
    updated_at     timestamptz NOT NULL,
    PRIMARY KEY (scope_id, execution_id, reservation_id),
    FOREIGN KEY (scope_id, execution_id) REFERENCES b6_resource_budget(scope_id, execution_id) ON DELETE RESTRICT,
    CHECK (updated_at >= created_at)
);
CREATE INDEX b6_resource_budget_reservation_status_idx
    ON b6_resource_budget_reservation(scope_id, execution_id, status);

CREATE TABLE b6_resource_budget_reservation_item (
    scope_id       varchar(256) NOT NULL,
    execution_id   varchar(256) NOT NULL,
    reservation_id varchar(256) NOT NULL,
    unit           varchar(64) NOT NULL,
    reserved_value bigint NOT NULL CHECK (reserved_value >= 0),
    PRIMARY KEY (scope_id, execution_id, reservation_id, unit),
    FOREIGN KEY (scope_id, execution_id, reservation_id)
        REFERENCES b6_resource_budget_reservation(scope_id, execution_id, reservation_id) ON DELETE RESTRICT
);

CREATE TABLE b6_resource_budget_actual_item (
    scope_id       varchar(256) NOT NULL,
    execution_id   varchar(256) NOT NULL,
    reservation_id varchar(256) NOT NULL,
    unit           varchar(64) NOT NULL,
    actual_value   bigint NOT NULL CHECK (actual_value >= 0),
    PRIMARY KEY (scope_id, execution_id, reservation_id, unit),
    FOREIGN KEY (scope_id, execution_id, reservation_id)
        REFERENCES b6_resource_budget_reservation(scope_id, execution_id, reservation_id) ON DELETE RESTRICT
);
