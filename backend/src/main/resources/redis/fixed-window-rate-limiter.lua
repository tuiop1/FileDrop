local key = KEYS[1]

local ttl = tonumber(ARGV[1])

local hits = redis.call('INCR', key)

if hits == 1 then
    redis.call('PEXPIRE', key, ttl)
end

return hits
