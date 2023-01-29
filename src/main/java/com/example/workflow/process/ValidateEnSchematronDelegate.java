package com.example.workflow.process;

import com.example.workflow.config.ApplicationContextHolder;
import com.example.workflow.validation.ENXsltTransformerSingleton;
import com.example.workflow.validation.ValidationResults;
import com.example.workflow.validation.ValidationResultsImpl;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.XdmDestination;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XsltTransformer;
import org.camunda.bpm.engine.delegate.BpmnError;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.xml.transform.stream.StreamSource;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Logger;

import static com.example.workflow.validation.ValidationStatus.ERROR;

@Component
public class ValidateEnSchematronDelegate implements JavaDelegate {
    private final Logger LOGGER = Logger.getLogger(ValidateEnSchematronDelegate.class.getName());
    Map<String, ValidationResults> errorAndWarResults = new HashMap<>();

    @Override
    public void execute(DelegateExecution delegateExecution) throws Exception {
        ValidationResults results = new ValidationResultsImpl();

        Environment env = ApplicationContextHolder.getContext().getBean(Environment.class);
        String messageCode = "";
        try {
            String businessRulesName = "EN_16931";
            String language = "en";

            String invoice = (String) delegateExecution.getVariable("decoded-invoice");
            String schema = (String) delegateExecution.getVariable("en-rules");

            ENXsltTransformerSingleton.setSchematronFile(Path.of(schema));
            ENXsltTransformerSingleton.xsltLoad();
            XsltTransformer transformer = ENXsltTransformerSingleton.getTransformer();
            LOGGER.info("Start EN validation ******************");


            synchronized (transformer) {

                transformer.setSource(new StreamSource(new ByteArrayInputStream(invoice.getBytes())));
                XdmDestination chainResult = new XdmDestination();
                transformer.setDestination(chainResult);
                transformer.transform();

                XdmNode rootnode = chainResult.getXdmNode();
                for (XdmNode node : rootnode.children().iterator().next().children()) {
                    if (node.getNodeName() == null || !("failed-assert".equals(node.getNodeName().getLocalName())))
                        continue;

                    messageCode = node.getAttributeValue(new QName("id"));
                    String messageCategory = businessRulesName;
                    String messageFlag = node.getAttributeValue(new QName("flag"));
                    Map params = new HashMap();
                    params.put("errorCode", messageCode);
                    String message = "";


                        if (language.equalsIgnoreCase("ar") )
                            message = "Error";
                        else
                            message = "Error in english";

                    if (messageFlag.equalsIgnoreCase("WARNING")) {
                        if (!errorAndWarResults.containsKey(messageCode)) {
                            results.addWarningMessage(messageCode, messageCategory, message);
                            errorAndWarResults.put(messageCode, results);
                        }
                    } else if (messageFlag.equalsIgnoreCase("INFO")) {
                        results.addInfoMessage(messageCode, messageCategory, message);
                    } else {
                        if (!errorAndWarResults.containsKey(messageCode)) {
                            results.addErrorMessage(messageCode, messageCategory, message);
                            errorAndWarResults.put(messageCode, results);
                        }
                    }


                }

            }
        } catch (Exception e) {
            LOGGER.info("Error : {} : "+ e.getMessage());
            e.printStackTrace(); // For Test
            results.addErrorMessage("GENERAL", "BUSINESS_RULES",
                    "Unable to execute Business Rules validation ->" + messageCode);
        }

//        delegateExecution.setVariable("validationStatus",results);
        LOGGER.info("End EN validation ****************** : {}"+ results);


    }
}