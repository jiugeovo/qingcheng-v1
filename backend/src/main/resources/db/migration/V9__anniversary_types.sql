-- Keep existing rows and older clients classified as "other" without inferring from titles.
alter table anniversaries
    add column anniversary_type varchar(16) character set ascii collate ascii_bin not null default 'other',
    add constraint chk_anniversaries_type
        check (cast(anniversary_type as binary) in ('meeting', 'relationship', 'wedding', 'birthday', 'other'));
