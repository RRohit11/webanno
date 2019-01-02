/*
 * Copyright 2018
 * Ubiquitous Knowledge Processing (UKP) Lab and FG Language Technology
 * Technische Universität Darmstadt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.tudarmstadt.ukp.clarin.webanno.api.annotation.adapter;

import static de.tudarmstadt.ukp.clarin.webanno.api.annotation.rendering.model.VCommentType.ERROR;
import static de.tudarmstadt.ukp.clarin.webanno.api.annotation.util.WebAnnoCasUtil.isSameSentence;
import static java.util.Collections.emptyList;
import static org.apache.uima.fit.util.CasUtil.getType;
import static org.apache.uima.fit.util.CasUtil.select;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.jcas.JCas;
import org.springframework.stereotype.Component;

import de.tudarmstadt.ukp.clarin.webanno.api.annotation.exception.AnnotationException;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.exception.MultipleSentenceCoveredException;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.layer.ChainLayerSupport;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.layer.LayerSupport;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.model.VID;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.rendering.model.VComment;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.rendering.model.VDocument;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.rendering.model.VSpan;
import de.tudarmstadt.ukp.clarin.webanno.support.logging.LogMessage;

/**
 * Ensure that annotations do not cross sentence boundaries. For chain layers, this check applies
 * only to the chain elements. Chain links can still cross sentence boundaries.
 */
@Component
public class SpanCrossSentenceBehavior
    extends SpanLayerBehavior
{
    @Override
    public boolean accepts(LayerSupport<?> aLayerType)
    {
        return super.accepts(aLayerType) || aLayerType instanceof ChainLayerSupport;
    }
    
    @Override
    public CreateSpanAnnotationRequest onCreate(TypeAdapter aAdapter,
            CreateSpanAnnotationRequest aRequest)
        throws AnnotationException
    {
        if (aAdapter.getLayer().isCrossSentence()) {
            return aRequest;
        }
        
        if (!isSameSentence(aRequest.getJcas(), aRequest.getBegin(), aRequest.getEnd())) {
            throw new MultipleSentenceCoveredException("Annotation covers multiple sentences, "
                    + "limit your annotation to single sentence!");
        }

        return aRequest;
    }
    
    @Override
    public void onRender(TypeAdapter aAdapter, VDocument aResponse,
            Map<AnnotationFS, VSpan> annoToSpanIdx)
    {
        if (aAdapter.getLayer().isCrossSentence()) {
            return;
        }
        
        // Since we split spans into multiple ranges at sentence boundaries, we can simply check
        // if there are multiple ranges for a given span. This is cheaper than checking for
        // every annotation whether the begin/end offset is in the same sentence.
        for (Entry<AnnotationFS, VSpan> e : annoToSpanIdx.entrySet()) {
            if (e.getValue().getRanges().size() > 1) {
                aResponse.add(new VComment(new VID(e.getKey()), ERROR,
                        "Crossing sentence bounardies is not permitted."));
            }
        }
    }
    
    @Override
    public List<Pair<LogMessage, AnnotationFS>> onValidate(TypeAdapter aAdapter, JCas aJCas)
    {
        if (aAdapter.getLayer().isCrossSentence()) {
            return emptyList();
        }
        
        CAS cas = aJCas.getCas();
        Type type = getType(cas, aAdapter.getAnnotationTypeName());
        List<Pair<LogMessage, AnnotationFS>> messages = new ArrayList<>();
        
        for (AnnotationFS fs : select(cas, type)) {
            if (!isSameSentence(aJCas, fs.getBegin(), fs.getEnd())) {
                messages.add(Pair.of(
                        LogMessage.error(this, "Crossing sentence bounardies is not permitted."),
                        fs));
            }
        }

        return messages;
    }
}
