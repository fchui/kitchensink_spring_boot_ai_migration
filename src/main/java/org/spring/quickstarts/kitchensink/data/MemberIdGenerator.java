/*
 * Copyright 2015, Red Hat, Inc. and/or its affiliates, and individual
 * contributors by the @authors tag. See the copyright.txt in the
 * distribution for a full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.spring.quickstarts.kitchensink.data;

import org.bson.Document;
import org.spring.quickstarts.kitchensink.model.Member;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.mapping.event.BeforeConvertCallback;
import org.springframework.stereotype.Component;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;

/**
 * Gives new members a numeric id (1, 2, 3, ...) instead of a MongoDB ObjectId, so the REST API and its
 * {@code /rest/members/{id}} URLs keep the id format of the relational version. The last id is kept in the
 * {@code counters} collection and incremented atomically, so concurrent registrations never get the same id.
 */
@Component
public class MemberIdGenerator implements BeforeConvertCallback<Member> {

    static final String COUNTERS_COLLECTION = "counters";

    static final String MEMBERS_COUNTER = "members";

    // resolved lazily: the MongoTemplate looks up this callback while it is being created
    private final ObjectProvider<MongoOperations> mongoOperations;

    public MemberIdGenerator(ObjectProvider<MongoOperations> mongoOperations) {
        this.mongoOperations = mongoOperations;
    }

    @Override
    public Member onBeforeConvert(Member member, String collection) {
        if (member.getId() == null) {
            member.setId(nextId());
        }
        return member;
    }

    private long nextId() {
        Document counter = mongoOperations.getObject().getCollection(COUNTERS_COLLECTION).findOneAndUpdate(
                Filters.eq("_id", MEMBERS_COUNTER),
                Updates.inc("seq", 1L),
                new FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER));
        return counter.getLong("seq");
    }
}
