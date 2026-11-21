-- Compatibility phase: new nodes can authenticate by hash while old nodes still use token.
alter table auth_tokens
    add column token_hash char(64) character set ascii collate ascii_bin null,
    add unique key uk_auth_tokens_token_hash (token_hash);

update auth_tokens
set token_hash = sha2(token, 256)
where token_hash is null;
