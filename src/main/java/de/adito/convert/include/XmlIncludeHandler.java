package de.adito.convert.include;

import lombok.NonNull;
import org.w3c.dom.*;

import javax.xml.XMLConstants;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Handles the includes in XML files
 *
 * @author r.hartinger, 27.06.2024
 */
public class XmlIncludeHandler extends AbstractIncludeHandler
{
  /**
   * The pattern for detecting {@code include} and {@code includeAll} in XML files.
   */
  private static final Pattern XML_PATTERN = Pattern.compile("<\\s*(include|includeAll)");


  @Override
  protected @NonNull Pattern getPattern()
  {
    return XML_PATTERN;
  }

  @Override
  public void modifyContent(@NonNull Map<Path, Path> pConvertedFiles, @NonNull Path pInput, @NonNull Path pIncludeFile, @NonNull Path pNewIncludeFile)
      throws Exception
  {

    DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
    dbFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
    dbFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
    DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();

    // parse the document
    Document document = dBuilder.parse(pIncludeFile.toFile());

    // Normalize the document to ensure that all text nodes are correct
    document.getDocumentElement().normalize();

    // Get the root element
    Element root = document.getDocumentElement();

    // get all include nodes
    NodeList includeList = root.getElementsByTagName("include");

    for (int i = 0; i < includeList.getLength(); i++)
    {
      Node includeNode = includeList.item(i);
      if (includeNode.getNodeType() == Node.ELEMENT_NODE)
      {
        Element includeElement = (Element) includeNode;


        String fileValue = includeElement.getAttribute("file");
        boolean relativeToChangelogFileValue = Boolean.parseBoolean(includeElement.getAttribute("relativeToChangelogFile"));

        // set the new file value
        includeElement.setAttribute("file", this.changeFile(pConvertedFiles, pInput, pIncludeFile, fileValue, relativeToChangelogFileValue));
      }
    }


    TransformerFactory transformerFactory = TransformerFactory.newInstance();
    transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
    transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
    Transformer transformer = transformerFactory.newTransformer();

    // write the changed document back
    DOMSource source = new DOMSource(document);
    StreamResult result = new StreamResult(pNewIncludeFile.toFile());
    transformer.transform(source, result);
  }
}
