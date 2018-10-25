/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2017 ForgeRock AS.
 */
/*
 * simon.moffatt@forgerock.com
 *
 * Checks for the presence of the named cookie in the authentication request.  Doesn't check cookie value, only presence
 */

package org.forgerock.openam.auth.nodes;

import com.google.inject.assistedinject.Assisted;
import com.sun.identity.shared.debug.Debug;
import org.forgerock.guava.common.collect.ImmutableList;
import org.forgerock.json.JsonValue;
import org.forgerock.openam.annotations.sm.Attribute;
import org.forgerock.openam.auth.node.api.*;
import org.forgerock.util.i18n.PreferredLocales;

import javax.inject.Inject;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;


@Node.Metadata(outcomeProvider = HTTPRequestNode.OutcomeProvider.class,
        configClass = HTTPRequestNode.Config.class)
public class HTTPRequestNode implements Node {

    private final static String TRUE_OUTCOME_ID = "true";
    private final static String FALSE_OUTCOME_ID = "false";
    private final static String DEBUG_FILE = "HTTPRequestNode";
    protected Debug debug = Debug.getInstance(DEBUG_FILE);

    public interface Config {

        @Attribute(order = 100)
        default String URL() {
            return "https://server.domain:port/context";
        }

        @Attribute(order = 200)
        default String Body() {
            return "{\"message\": \"{{User}} has logged in\"}";
        }

        @Attribute(order = 300)
        Map<String, String> Headers();


    }

    private final Config config;

    /**
     * Create the node.
     * @param config The service config.
     */
    @Inject
    public HTTPRequestNode(@Assisted Config config) {
        this.config = config;
    }

    @Override
    public Action process(TreeContext context) {

        debug.message("[" + DEBUG_FILE + "]: Started");

        //Call helper function that sends HTTP request
        return sendRequest(context);


    }

    private Action sendRequest(TreeContext context) {

        try {

            URL url = new URL(config.URL());
            debug.message("[" + DEBUG_FILE + "]: Sending request to " + url);

            //Build HTTP request
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoInput(true);
            conn.setDoOutput(true);

            //Set the headers
            for (Map.Entry<String, String> entry : config.Headers().entrySet()) {
                conn.setRequestProperty(entry.getKey(),entry.getValue());
            }

            //Set body
            //Read the contents of the config body.  {{User}} is the only variable available
            String submittedBody=config.Body();
            String userName = context.sharedState.get(SharedStateConstants.USERNAME).asString();
            if (submittedBody.contains("{{User}}")) submittedBody = submittedBody.replace("{{User}}", userName);

            OutputStreamWriter writer = new OutputStreamWriter(conn.getOutputStream(), StandardCharsets.UTF_8);
            writer.write(submittedBody);
            writer.close();

            //Check response is 200
            if (conn.getResponseCode() == 200) {

                debug.message("[" + DEBUG_FILE + "]: response 200");
                return goTo(true).build();

            }

            debug.message("[" + DEBUG_FILE + "]: response not 200 :-(");
            conn.disconnect();
            return goTo(false).build();

        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return goTo(true).build();
    }


    private Action.ActionBuilder goTo(boolean outcome) {
        return Action.goTo(outcome ? TRUE_OUTCOME_ID : FALSE_OUTCOME_ID);
    }

    static final class OutcomeProvider implements org.forgerock.openam.auth.node.api.OutcomeProvider {
        private static final String BUNDLE = HTTPRequestNode.class.getName().replace(".", "/");

        @Override
        public List<Outcome> getOutcomes(PreferredLocales locales, JsonValue nodeAttributes) {
            ResourceBundle bundle = locales.getBundleInPreferredLocale(BUNDLE, OutcomeProvider.class.getClassLoader());
            return ImmutableList.of(
                    new Outcome(TRUE_OUTCOME_ID, bundle.getString("true")),
                    new Outcome(FALSE_OUTCOME_ID, bundle.getString("false")));
        }
    }
}
