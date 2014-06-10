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
package test;

import org.labkey.biotrust.GetDocumentsCommand;
import org.labkey.biotrust.GetDocumentsResponse;
import org.labkey.remoteapi.CommandException;
import org.labkey.remoteapi.Connection;
import org.labkey.remoteapi.query.SelectRowsCommand;
import org.labkey.remoteapi.query.SelectRowsResponse;

import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: 7/22/13
 */
public class Test
{
    /**
     *
     * @param args - username, password, sampleRequestId
     * @throws Exception
     */
    public static void main(String[] args) throws Exception
    {
        Connection cn = new Connection("http://localhost:8080/labkey", args[0], args[1]);

        try {

            GetDocumentsCommand command = new GetDocumentsCommand();

            // get all documents in the document set for the study
            command.setStudyId(Integer.parseInt(args[2]));
            command.setOwnerType(GetDocumentsCommand.OwnerType.study);

            GetDocumentsResponse response = command.execute(cn, null);
            System.out.println("Document set for the study");
            for (GetDocumentsResponse.Document doc : response.getDocuments())
            {
                System.out.println(doc.toString());
            }

            // get just the consent documents for the study (matched by document type name)
            command = new GetDocumentsCommand();
            command.setStudyId(Integer.parseInt(args[2]));
            command.setOwnerType(GetDocumentsCommand.OwnerType.study);
            command.setDocumentTypeName("Blank Unique Consent Form (by Study)");

            response = command.execute(cn, null);
            System.out.println("Consent forms for the study");
            for (GetDocumentsResponse.Document doc : response.getDocuments())
            {
                System.out.println(doc.toString());
            }

            // get the protocol documents for the sample request
            command = new GetDocumentsCommand();
            command.setSampleRequestId(Integer.parseInt(args[2]));
            command.setOwnerType(GetDocumentsCommand.OwnerType.samplerequest);

            response = command.execute(cn, null);
            System.out.println("Protocol documents for the sample request");
            for (GetDocumentsResponse.Document doc : response.getDocuments())
            {
                System.out.println(doc.toString());
            }
        }
        catch(CommandException e)
        {
            System.out.println("Command Exception: " + e.getMessage());
        }
    }
}
