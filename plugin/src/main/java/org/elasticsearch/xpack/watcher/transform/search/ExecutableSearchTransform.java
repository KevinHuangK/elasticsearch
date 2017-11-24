/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.watcher.transform.search;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessage;
import org.apache.logging.log4j.util.Supplier;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.bytes.BytesArray;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.script.Script;
import org.elasticsearch.xpack.watcher.WatcherClientHelper;
import org.elasticsearch.xpack.watcher.execution.WatchExecutionContext;
import org.elasticsearch.xpack.watcher.support.search.WatcherSearchTemplateRequest;
import org.elasticsearch.xpack.watcher.support.search.WatcherSearchTemplateService;
import org.elasticsearch.xpack.watcher.transform.ExecutableTransform;
import org.elasticsearch.xpack.watcher.watch.Payload;

import static org.elasticsearch.xpack.watcher.transform.search.SearchTransform.TYPE;

public class ExecutableSearchTransform extends ExecutableTransform<SearchTransform, SearchTransform.Result> {

    static final SearchType DEFAULT_SEARCH_TYPE = SearchType.QUERY_THEN_FETCH;

    private final Client client;
    private final WatcherSearchTemplateService searchTemplateService;
    private final TimeValue timeout;

    public ExecutableSearchTransform(SearchTransform transform, Logger logger, Client client,
                                     WatcherSearchTemplateService searchTemplateService, TimeValue defaultTimeout) {
        super(transform, logger);
        this.client = client;
        this.searchTemplateService = searchTemplateService;
        this.timeout = transform.getTimeout() != null ? transform.getTimeout() : defaultTimeout;
    }

    @Override
    public SearchTransform.Result execute(WatchExecutionContext ctx, Payload payload) {
        WatcherSearchTemplateRequest request = null;
        try {
            Script template = transform.getRequest().getOrCreateTemplate();
            String renderedTemplate = searchTemplateService.renderTemplate(template, ctx, payload);
            // We need to make a copy, so that we don't modify the original instance that we keep around in a watch:
            request = new WatcherSearchTemplateRequest(transform.getRequest(), new BytesArray(renderedTemplate));
            SearchRequest searchRequest = searchTemplateService.toSearchRequest(request);
            SearchResponse resp = WatcherClientHelper.execute(ctx.watch(), client, () -> client.search(searchRequest).actionGet(timeout));
            return new SearchTransform.Result(request, new Payload.XContent(resp));
        } catch (Exception e) {
            logger.error((Supplier<?>) () -> new ParameterizedMessage("failed to execute [{}] transform for [{}]", TYPE, ctx.id()), e);
            return new SearchTransform.Result(request, e);
        }
    }
}
