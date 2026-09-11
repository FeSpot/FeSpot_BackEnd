alter table report_locations add column last_reported_at datetime null;

update report_locations rl
set last_reported_at = coalesce (
    (select max(r.created_at) from reports r where r.report_location_id = rl.report_location_id),
    rl.created_at
);

alter table report_locations modify column last_reported_at datetime not null;