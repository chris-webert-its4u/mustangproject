package org.mustangproject.ZUGFeRD;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.mustangproject.Allowance;
import org.mustangproject.Charge;
import org.mustangproject.EStandard;
import org.mustangproject.Invoice;
import org.mustangproject.Item;
import org.mustangproject.Product;
import org.mustangproject.ReferencedDocument;
import org.mustangproject.SchemedID;
import org.mustangproject.TradeParty;
import org.mustangproject.XMLTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class ZUGFeRDInvoiceImporter extends ZUGFeRDImporter {
	private static final Logger LOGGER = LoggerFactory.getLogger(ZUGFeRDInvoiceImporter.class.getCanonicalName()); // log
	private boolean recalcPrice = false;
	private boolean ignoreCalculationErrors = false;

	public ZUGFeRDInvoiceImporter() {
		super();
	}

	public ZUGFeRDInvoiceImporter(String filename) {
		super(filename);
	}

	public ZUGFeRDInvoiceImporter(InputStream stream) {
		super(stream);
	}

	public void fromXML(String XML) {
		try {
			containsMeta = true;
			setRawXML(XML.getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			LOGGER.error(e.getMessage(), e);
		}
	}


	/***
	 * This will parse a XML into the given invoice object
	 * @param zpp the invoice to be altered
	 * @return the parsed invoice object
	 * @throws XPathExpressionException if xpath could not be evaluated
	 * @throws ParseException if the grand total of the parsed invoice could not be replicated with the new invoice
	 */
	public Invoice extractInto(Invoice zpp) throws XPathExpressionException, ParseException {

		String number = "";
		/*
		 * dummywerte sind derzeit noch setDueDate setIssueDate setDeliveryDate
		 * setSender setRecipient setnumber bspw. due date
		 * //ExchangedDocument//IssueDateTime//DateTimeString : due date optional
		 */
		XPathFactory xpathFact = XPathFactory.newInstance();
		XPath xpath = xpathFact.newXPath();
		XPathExpression xpr = xpath.compile("//*[local-name()=\"SellerTradeParty\"]|//*[local-name()=\"AccountingSupplierParty\"]");
		NodeList SellerNodes = (NodeList) xpr.evaluate(getDocument(), XPathConstants.NODESET);

		xpr = xpath.compile("//*[local-name()=\"BuyerTradeParty\"]|//*[local-name()=\"AccountingCustomerParty\"]");
		NodeList BuyerNodes = (NodeList) xpr.evaluate(getDocument(), XPathConstants.NODESET);
		xpr = xpath.compile("//*[local-name()=\"ExchangedDocument\"]|//*[local-name()=\"HeaderExchangedDocument\"]");
		NodeList ExchangedDocumentNodes = (NodeList) xpr.evaluate(getDocument(), XPathConstants.NODESET);

		xpr = xpath.compile("//*[local-name()=\"GrandTotalAmount\"]|//*[local-name()=\"PayableAmount\"]");
		BigDecimal expectedGrandTotal = null;
		NodeList totalNodes = (NodeList) xpr.evaluate(getDocument(), XPathConstants.NODESET);
		if (totalNodes.getLength() > 0) {
			expectedGrandTotal = new BigDecimal(totalNodes.item(0).getTextContent());
		}

		Date issueDate = null;
		Date dueDate = null;
		Date deliveryDate = null;
		for (int i = 0; i < ExchangedDocumentNodes.getLength(); i++) {

			// nodes.item(i).getTextContent())) {
			Node exchangedDocumentNode = ExchangedDocumentNodes.item(i);
			NodeList exchangedDocumentChilds = exchangedDocumentNode.getChildNodes();
			for (int documentChildIndex = 0; documentChildIndex < exchangedDocumentChilds
				.getLength(); documentChildIndex++) {
				Node item = exchangedDocumentChilds.item(documentChildIndex);
				if ((item.getLocalName() != null) && (item.getLocalName().equals("ID"))) {
					number = item.getTextContent();
				}
				if ((item.getLocalName() != null) && (item.getLocalName().equals("IssueDateTime"))) {
					NodeList issueDateTimeChilds = item.getChildNodes();
					for (int issueDateChildIndex = 0; issueDateChildIndex < issueDateTimeChilds
						.getLength(); issueDateChildIndex++) {
						if ((issueDateTimeChilds.item(issueDateChildIndex).getLocalName() != null)
							&& (issueDateTimeChilds.item(issueDateChildIndex).getLocalName()
							.equals("DateTimeString"))) {
							issueDate = new SimpleDateFormat("yyyyMMdd")
								.parse(issueDateTimeChilds.item(issueDateChildIndex).getTextContent());
						}
					}
				}
			}
		}
		String rootNode=extractString("local-name(/*)");
		if (rootNode.equals("Invoice"))
		{
		// UBL...
			number = extractString("//*[local-name()=\"Invoice\"]/*[local-name()=\"ID\"]").trim();
			issueDate=new SimpleDateFormat("yyyy-MM-dd")
				.parse(extractString("//*[local-name()=\"Invoice\"]/*[local-name()=\"IssueDate\"]").trim());
			dueDate=new SimpleDateFormat("yyyy-MM-dd")
				.parse(extractString("//*[local-name()=\"Invoice\"]/*[local-name()=\"DueDate\"]").trim());
			deliveryDate=new SimpleDateFormat("yyyy-MM-dd")
				.parse(extractString("//*[local-name()=\"Delivery\"]/*[local-name()=\"ActualDeliveryDate\"]").trim());
		}
		xpr = xpath.compile("//*[local-name()=\"ApplicableHeaderTradeDelivery\"]");
		NodeList headerTradeDeliveryNodes = (NodeList) xpr.evaluate(getDocument(), XPathConstants.NODESET);

		for (int i = 0; i < headerTradeDeliveryNodes.getLength(); i++) {
			// nodes.item(i).getTextContent())) {
			Node headerTradeDeliveryNode = headerTradeDeliveryNodes.item(i);
			NodeList headerTradeDeliveryChilds = headerTradeDeliveryNode.getChildNodes();
			for (int deliveryChildIndex = 0; deliveryChildIndex < headerTradeDeliveryChilds
				.getLength(); deliveryChildIndex++) {
				if ((headerTradeDeliveryChilds.item(deliveryChildIndex).getLocalName() != null)
					&& (headerTradeDeliveryChilds.item(deliveryChildIndex).getLocalName()
					.equals("ActualDeliverySupplyChainEvent"))) {
					NodeList actualDeliveryChilds = headerTradeDeliveryChilds.item(deliveryChildIndex).getChildNodes();
					for (int actualDeliveryChildIndex = 0; actualDeliveryChildIndex < actualDeliveryChilds
						.getLength(); actualDeliveryChildIndex++) {
						if ((actualDeliveryChilds.item(actualDeliveryChildIndex).getLocalName() != null)
							&& (actualDeliveryChilds.item(actualDeliveryChildIndex).getLocalName()
							.equals("OccurrenceDateTime"))) {
							NodeList occurenceChilds = actualDeliveryChilds.item(actualDeliveryChildIndex)
								.getChildNodes();
							for (int occurenceChildIndex = 0; occurenceChildIndex < occurenceChilds
								.getLength(); occurenceChildIndex++) {
								if ((occurenceChilds.item(occurenceChildIndex).getLocalName() != null)
									&& (occurenceChilds.item(occurenceChildIndex).getLocalName()
									.equals("DateTimeString"))) {
									deliveryDate = new SimpleDateFormat("yyyyMMdd")
										.parse(occurenceChilds.item(occurenceChildIndex).getTextContent());
								}
							}
						}
					}
				}
			}
		}

		xpr = xpath.compile("//*[local-name()=\"ApplicableHeaderTradeAgreement\"]");
		NodeList headerTradeAgreementNodes = (NodeList) xpr.evaluate(getDocument(), XPathConstants.NODESET);
		String buyerOrderIssuerAssignedID = null;
		String sellerOrderIssuerAssignedID = null;
		for (int i = 0; i < headerTradeAgreementNodes.getLength(); i++) {
			// nodes.item(i).getTextContent())) {
			Node headerTradeAgreementNode = headerTradeAgreementNodes.item(i);
			NodeList headerTradeAgreementChilds = headerTradeAgreementNode.getChildNodes();
			for (int agreementChildIndex = 0; agreementChildIndex < headerTradeAgreementChilds
				.getLength(); agreementChildIndex++) {
				if ((headerTradeAgreementChilds.item(agreementChildIndex).getLocalName() != null)
					&& (headerTradeAgreementChilds.item(agreementChildIndex).getLocalName()
					.equals("BuyerOrderReferencedDocument"))) {
					NodeList buyerOrderChilds = headerTradeAgreementChilds.item(agreementChildIndex).getChildNodes();
					for (int buyerOrderChildIndex = 0; buyerOrderChildIndex < buyerOrderChilds
						.getLength(); buyerOrderChildIndex++) {
						if ((buyerOrderChilds.item(buyerOrderChildIndex).getLocalName() != null)
							&& (buyerOrderChilds.item(buyerOrderChildIndex).getLocalName()
							.equals("IssuerAssignedID"))) {
							buyerOrderIssuerAssignedID = buyerOrderChilds.item(buyerOrderChildIndex).getTextContent();
						}
					}
				}
				if ((headerTradeAgreementChilds.item(agreementChildIndex).getLocalName() != null)
					&& (headerTradeAgreementChilds.item(agreementChildIndex).getLocalName()
					.equals("SellerOrderReferencedDocument"))) {
					NodeList sellerOrderChilds = headerTradeAgreementChilds.item(agreementChildIndex).getChildNodes();
					for (int sellerOrderChildIndex = 0; sellerOrderChildIndex < sellerOrderChilds
						.getLength(); sellerOrderChildIndex++) {
						if ((sellerOrderChilds.item(sellerOrderChildIndex).getLocalName() != null)
							&& (sellerOrderChilds.item(sellerOrderChildIndex).getLocalName()
							.equals("IssuerAssignedID"))) {
							sellerOrderIssuerAssignedID = sellerOrderChilds.item(sellerOrderChildIndex).getTextContent();
						}
					}
				}
			}

		}


		xpr = xpath.compile("//*[local-name()=\"ApplicableHeaderTradeSettlement\"]|//*[local-name()=\"ApplicableSupplyChainTradeSettlement\"]");
		NodeList headerTradeSettlementNodes = (NodeList) xpr.evaluate(getDocument(), XPathConstants.NODESET);

		for (int i = 0; i < headerTradeSettlementNodes.getLength(); i++) {
			// nodes.item(i).getTextContent())) {
			Node headerTradeSettlementNode = headerTradeSettlementNodes.item(i);
			NodeList headerTradeSettlementChilds = headerTradeSettlementNode.getChildNodes();
			for (int settlementChildIndex = 0; settlementChildIndex < headerTradeSettlementChilds
				.getLength(); settlementChildIndex++) {
				if ((headerTradeSettlementChilds.item(settlementChildIndex).getLocalName() != null)
					&& (headerTradeSettlementChilds.item(settlementChildIndex).getLocalName()
					.equals("SpecifiedTradePaymentTerms"))) {
					NodeList paymentTermChilds = headerTradeSettlementChilds.item(settlementChildIndex).getChildNodes();
					for (int paymentTermChildIndex = 0; paymentTermChildIndex < paymentTermChilds
						.getLength(); paymentTermChildIndex++) {
						if ((paymentTermChilds.item(paymentTermChildIndex).getLocalName() != null) && (paymentTermChilds
							.item(paymentTermChildIndex).getLocalName().equals("DueDateDateTime"))) {
							NodeList dueDateChilds = paymentTermChilds.item(paymentTermChildIndex).getChildNodes();
							for (int dueDateChildIndex = 0; dueDateChildIndex < dueDateChilds
								.getLength(); dueDateChildIndex++) {
								if ((dueDateChilds.item(dueDateChildIndex).getLocalName() != null) && (dueDateChilds
									.item(dueDateChildIndex).getLocalName().equals("DateTimeString"))) {
									dueDate = new SimpleDateFormat("yyyyMMdd")
										.parse(dueDateChilds.item(dueDateChildIndex).getTextContent());
								}
							}
						}
					}
				}
			}
		}

		zpp.setDueDate(dueDate).setDeliveryDate(deliveryDate).setIssueDate(issueDate)
			.setSender(new TradeParty(SellerNodes)).setRecipient(new TradeParty(BuyerNodes)).setNumber(number);
		if (buyerOrderIssuerAssignedID != null) {
			zpp.setBuyerOrderReferencedDocumentID(buyerOrderIssuerAssignedID);
		}
		if (sellerOrderIssuerAssignedID != null) {
			zpp.setSellerOrderReferencedDocumentID(sellerOrderIssuerAssignedID);
		}

//.addItem(new Item(new Product("Testprodukt","","C62",BigDecimal.ZERO),amount,new BigDecimal(1.0)))
		zpp.setOwnOrganisationName(extractString("//*[local-name()=\"SellerTradeParty\"]/*[local-name()=\"Name\"]|//*[local-name()=\"AccountingSupplierParty\"]/*[local-name()=\"Party\"]/*[local-name()=\"PartyName\"]").trim());

		xpr = xpath.compile("//*[local-name()=\"BuyerReference\"]");
		String buyerReference = null;
		totalNodes = (NodeList) xpr.evaluate(getDocument(), XPathConstants.NODESET);
		if (totalNodes.getLength() > 0) {
			buyerReference = totalNodes.item(0).getTextContent();
		}
		if (buyerReference != null) {
			zpp.setReferenceNumber(buyerReference);
		}

		xpr = xpath.compile("//*[local-name()=\"IncludedSupplyChainTradeLineItem\"]|//*[local-name()=\"InvoiceLine\"]");
		NodeList nodes = (NodeList) xpr.evaluate(getDocument(), XPathConstants.NODESET);

		if (nodes.getLength() == 0) {
		} else {
			for (int i = 0; i < nodes.getLength(); i++) {

				Node currentItemNode = nodes.item(i);
				Item it = new Item(currentItemNode.getChildNodes(), recalcPrice);
				zpp.addItem(it);

			}

			// item level charges+allowances are not yet handled but a lower item price will
			// be read,
			// so the invoice remains arithmetically correct
			// -> parse document level charges+allowances
			xpr = xpath.compile("//*[local-name()=\"SpecifiedTradeAllowanceCharge\"]");
			NodeList chargeNodes = (NodeList) xpr.evaluate(getDocument(), XPathConstants.NODESET);
			for (int i = 0; i < chargeNodes.getLength(); i++) {
				NodeList chargeNodeChilds = chargeNodes.item(i).getChildNodes();
				boolean isCharge = true;
				String chargeAmount = null;
				String reason = null;
				String taxPercent = null;
				for (int chargeChildIndex = 0; chargeChildIndex < chargeNodeChilds.getLength(); chargeChildIndex++) {
					String chargeChildName = chargeNodeChilds.item(chargeChildIndex).getLocalName();
					if (chargeChildName != null) {

						if (chargeChildName.equals("ChargeIndicator")) {
							NodeList indicatorChilds = chargeNodeChilds.item(chargeChildIndex).getChildNodes();
							for (int indicatorChildIndex = 0; indicatorChildIndex < indicatorChilds
								.getLength(); indicatorChildIndex++) {
								if ((indicatorChilds.item(indicatorChildIndex).getLocalName() != null)
									&& (indicatorChilds.item(indicatorChildIndex).getLocalName()
									.equals("Indicator"))) {
									isCharge = indicatorChilds.item(indicatorChildIndex).getTextContent()
										.equalsIgnoreCase("true");
								}
							}
						} else if (chargeChildName.equals("ActualAmount")) {
							chargeAmount = chargeNodeChilds.item(chargeChildIndex).getTextContent();
						} else if (chargeChildName.equals("Reason")) {
							reason = chargeNodeChilds.item(chargeChildIndex).getTextContent();
						} else if (chargeChildName.equals("CategoryTradeTax")) {
							NodeList taxChilds = chargeNodeChilds.item(chargeChildIndex).getChildNodes();
							for (int taxChildIndex = 0; taxChildIndex < taxChilds.getLength(); taxChildIndex++) {
								String taxItemName = taxChilds.item(taxChildIndex).getLocalName();
								if ((taxItemName != null) && (taxItemName.equals("RateApplicablePercent")
									|| taxItemName.equals("ApplicablePercent"))) {
									taxPercent = taxChilds.item(taxChildIndex).getTextContent();
								}
							}
						}
					}
				}

				if (isCharge) {
					Charge c = new Charge(new BigDecimal(chargeAmount));
					if (reason != null) {
						c.setReason(reason);
					}
					if (taxPercent != null) {
						c.setTaxPercent(new BigDecimal(taxPercent));
					}

					zpp.addCharge(c);
				} else {
					Allowance a = new Allowance(new BigDecimal(chargeAmount));
					if (reason != null) {
						a.setReason(reason);
					}
					if (taxPercent != null) {
						a.setTaxPercent(new BigDecimal(taxPercent));
					}
					zpp.addAllowance(a);
				}

			}

			TransactionCalculator tc = new TransactionCalculator(zpp);
			String expectedStringTotalGross = tc.getGrandTotal().toPlainString();
			EStandard whichType;
			try {
				whichType = getStandard();
			} catch (Exception e) {
				throw new ParseException("Could not find out if it's an invoice, order, or delivery advice", 0);

			}

			if ((whichType != EStandard.despatchadvice)
				&& ((!expectedStringTotalGross.equals(XMLTools.nDigitFormat(expectedGrandTotal, 2)))
				&& (!ignoreCalculationErrors))) {
				throw new ParseException(
					"Could not reproduce the invoice, this could mean that it could not be read properly", 0);
			}
		}
		return zpp;

	}

	/***
	 * This will parse a XML into a invoice object
	 *
	 * @return the parsed invoice object
	 * @throws XPathExpressionException if internal xpath expressions were wrong
	 * @throws ParseException if the grand total of the parsed invoice could not be replicated with the new invoice
	 */
	public Invoice extractInvoice() throws XPathExpressionException, ParseException {
		Invoice i = new Invoice();
		return extractInto(i);


	}

	/***
	 * have the item prices be determined from the line total.
	 * That's a workaround for some invoices which just put 0 as item price
	 */
	public void doRecalculateItemPricesFromLineTotals() {
		recalcPrice = true;
	}

	/***
	 * do not raise ParseExceptions even if the reproduced invoice total does not match the given value
	 */
	public void doIgnoreCalculationErrors() {
		ignoreCalculationErrors = true;
	}
}
