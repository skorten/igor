/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.igor.build

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool


/**
 * Shared cache of build details
 */
@SuppressWarnings(['PropertyName', 'DuplicateNumberLiteral'])
@Service
class BuildCache {

    @Autowired
    JedisPool jedisPool

    @SuppressWarnings('GStringExpressionWithinString')
    @Value('${spinnaker.jedis.prefix:igor}')
    String prefix

    String id = 'builds'

    List<String> getJobNames(String master) {
        Jedis resource = jedisPool.resource
        List<String> jobs = resource.keys("${baseKey()}:${master}:*").collect { extractJobName(it) }.sort()
        jedisPool.returnResource(resource)
        jobs
    }

    List<String> getTypeaheadResults(String search) {
        Jedis resource = jedisPool.resource
        List<String> results = resource.keys("${baseKey()}:*:*${search.toUpperCase()}*:*").collect {
            extractTypeaheadResult(it)
        }.sort()
        jedisPool.returnResource(resource)
        results
    }

    Map getLastBuild(String master, String job) {
        Jedis resource = jedisPool.resource
        if (!resource.exists(makeKey(master, job))) {
            jedisPool.returnResource(resource)
            return [:]
        }
        Map result = resource.hgetAll(makeKey(master, job))
        Map convertedResult = [
            lastBuildLabel: Integer.parseInt(result.lastBuildLabel),
            lastBuildBuilding: Boolean.parseBoolean(result.lastBuildBuilding)
        ]
        jedisPool.returnResource(resource)
        convertedResult
    }

    void setLastBuild(String master, String job, int lastBuild, boolean building) {
        Jedis resource = jedisPool.resource
        String key = makeKey(master, job)
        resource.hset(key, 'lastBuildLabel', lastBuild as String)
        resource.hset(key, 'lastBuildBuilding', building as String)
        jedisPool.returnResource(resource)
    }

    void remove(String master, String job) {
        Jedis resource = jedisPool.resource
        resource.del(makeKey(master, job))
        jedisPool.returnResource(resource)
    }

    private String makeKey(String master, String job) {
        "${baseKey()}:${master}:${job.toUpperCase()}:${job}"
    }

    private static String extractJobName(String key) {
        key.split(':')[4]
    }

    private static String extractTypeaheadResult(String key) {
        def parts = key.split(':')
        "${parts[2]}:${parts[4]}"
    }

    private String baseKey() {
        return "${prefix}:${id}"
    }
}
