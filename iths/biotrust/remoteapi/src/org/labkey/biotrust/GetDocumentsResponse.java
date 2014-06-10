/*
 * Copyright (c) 2013 LabKey Corporation
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
package org.labkey.biotrust;

import org.json.simple.JSONObject;
import org.labkey.remoteapi.CommandResponse;
import org.labkey.remoteapi.ResponseObject;

import java.lang.Boolean;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: 7/22/13
 */
public class GetDocumentsResponse extends CommandResponse
{
    public GetDocumentsResponse(String text, int statusCode, String contentType, JSONObject json, GetDocumentsCommand sourceCommand)
    {
        super(text, statusCode, contentType, json, sourceCommand);
    }

    /** @return the documents that matched the request */
    public List<Document> getDocuments()
    {
        List<Map<String, Object>> documentsNode = (List<Map<String, Object>>)getParsedData().get("documentSet");
        List<Document> result = new ArrayList<>();
        for (Map<String, Object> map : documentsNode)
        {
            result.add(new Document(map));
        }
        return result;
    }


    public static class Document extends ResponseObject
    {
        private Date _created;
        private String _createdBy;
        private String _downloadUrl;
        private String _name;
        private String _type;
        private boolean _active;

        private Document(Map<String, Object> map)
        {
            super(map);

            long date = Date.parse(String.valueOf(map.get("created")));
            _created = new Date(date);
            _createdBy = String.valueOf(map.get("createdBy"));
            _downloadUrl = String.valueOf(map.get("downloadURL"));
            _name = String.valueOf(map.get("name"));
            _type = String.valueOf(map.get("type"));
            _active = Boolean.valueOf(String.valueOf(map.get("active")));
        }

        public Date getCreated()
        {
            return _created;
        }

        public String getCreatedBy()
        {
            return _createdBy;
        }

        public String getDownloadUrl()
        {
            return _downloadUrl;
        }

        public String getName()
        {
            return _name;
        }

        public String getType()
        {
            return _type;
        }

        public boolean isActive()
        {
            return _active;
        }

        @Override
        public String toString()
        {
            StringBuilder sb = new StringBuilder();

            sb.append("name").append('\t').append(_name).append('\n');
            sb.append("createdBy").append('\t').append(_createdBy).append('\n');
            sb.append("created").append('\t').append(_created).append('\n');
            sb.append("type").append('\t').append(_type).append('\n');
            sb.append("downloadURL").append('\t').append(_downloadUrl).append('\n');
            sb.append("active").append('\t').append(_active).append('\n');

            return sb.toString();
        }
    }
}
