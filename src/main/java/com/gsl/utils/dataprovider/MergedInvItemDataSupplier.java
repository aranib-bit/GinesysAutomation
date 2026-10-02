package com.gsl.utils.dataprovider;

import com.gsl.config.ConfigFactory;
import com.gsl.testdata.MasterTestData;
import com.gsl.testdata.Inventory.Product_Definition.Item_Hierarchy.Item.ItemCategoryDescription;
import com.gsl.testdata.Inventory.Product_Definition.Item_Hierarchy.Item.ItemGeneral;
import com.gsl.testdata.Inventory.Product_Definition.Item_Hierarchy.Item.ItemInventoryTracking;
import com.gsl.testdata.Inventory.Product_Definition.Item_Hierarchy.Item.ItemOthers;
import com.gsl.testdata.Inventory.Product_Definition.Item_Hierarchy.Item.ItemPOSBehaviour;
import com.gsl.testdata.Inventory.Product_Definition.Item_Hierarchy.Item.ItemPricing;
import com.gsl.testdata.Inventory.Product_Definition.Item_Hierarchy.Item.ItemStoreWiseMultiplePrice;
import com.gsl.testdata.Inventory.MergedInventoryItemData;
import com.gsl.validation.MandatoryFieldValidator;

import io.github.sskorol.core.DataSupplier;
import io.github.sskorol.data.XlsxReader;
import one.util.streamex.StreamEx;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.github.sskorol.data.TestDataReader.use;

/**
 * DataSupplier for Inventory Item creation tests (e.g. {@code CreateItem}) — filters the shared
 * {@code MasterTestData} sheet to rows whose {@code testcasename} matches the invoking test
 * method's name, then for each matched {@code TestCaseId} joins in that item's General/Category
 * Description/Others/Pricing/Inventory Tracking/POS Behaviour rows (each optional except {@link
 * com.gsl.testdata.Inventory.Product_Definition.Item_Hierarchy.Item.ItemGeneral}, which is
 * mandatory) and its Store-Wise Multiple Price rows (0..N), all read from the single
 * {@code ConfigFactory.getExcelDataConfig().inventoryitemPath()} workbook (each a different
 * sheet). Every joined non-null sheet is passed through {@link MandatoryFieldValidator#validate}
 * before being emitted.
 *
 * <p>{@code flatMap=true} — a {@code TestCaseId} whose {@code MasterTestData.getInvocationcount()}
 * is greater than 1 is expanded into that many separate invocations (1-based {@code
 * invocationIndex}/{@code totalInvocations}), each otherwise carrying identical joined data —
 * mirrors {@link MergedReceiptDataSupplier}'s invocation-count expansion.
 */
public class MergedInvItemDataSupplier {

    /**
     * Filters {@code MasterTestData} to {@code method}'s name, joins each matched row's item-detail
     * sheets, and expands per-row by {@code invocationcount} — see the class Javadoc.
     *
     * @param method the TestNG-injected test method; only its name is used to match {@code
     *               testcasename} in the {@code MasterTestData} sheet
     * @throws IllegalStateException if a matched {@code TestCaseId} has no corresponding row in
     *                                the mandatory {@code ItemGeneral} sheet
     */
    @DataSupplier(name = "dataSupplier", flatMap = true)
    public StreamEx<MergedInventoryItemData> dataSupplier(Method method) {

        String methodName = method.getName();
        System.out.println("Incoming Method Name: " + methodName);

        List<MasterTestData> allTestCases = use(XlsxReader.class)
                .withTarget(MasterTestData.class)
                .withSource(ConfigFactory.getExcelDataConfig().MasterTestData())
                .read()
                .toList();

        String inventoryPath = ConfigFactory.getExcelDataConfig().inventoryitemPath();

        List<ItemGeneral> generalRows = use(XlsxReader.class)
                .withTarget(ItemGeneral.class)
                .withSource(inventoryPath)
                .read()
                .toList();

        List<ItemCategoryDescription> categoryDescRows = use(XlsxReader.class)
                .withTarget(ItemCategoryDescription.class)
                .withSource(inventoryPath)
                .read()
                .toList();

        List<ItemOthers> otherRows = use(XlsxReader.class)
                .withTarget(ItemOthers.class)
                .withSource(inventoryPath)
                .read()
                .toList();

        List<ItemPricing> pricingRows = use(XlsxReader.class)
                .withTarget(ItemPricing.class)
                .withSource(inventoryPath)
                .read()
                .toList();

        List<ItemInventoryTracking> trackingRows = use(XlsxReader.class)
                .withTarget(ItemInventoryTracking.class)
                .withSource(inventoryPath)
                .read()
                .toList();

        List<ItemPOSBehaviour> posBehaviourRows = use(XlsxReader.class)
                .withTarget(ItemPOSBehaviour.class)
                .withSource(inventoryPath)
                .read()
                .toList();

        List<ItemStoreWiseMultiplePrice> storeWisePriceRows = use(XlsxReader.class)
                .withTarget(ItemStoreWiseMultiplePrice.class)
                .withSource(inventoryPath)
                .read()
                .toList();

        List<MasterTestData> filteredCases = allTestCases.stream()
                .filter(tc -> methodName.equalsIgnoreCase(tc.getTestcasename()))
                .collect(Collectors.toList());

        return StreamEx.of(filteredCases)
                .flatMap(testCase -> {

                    String testCaseId = testCase.getTestcaseid();

                    ItemGeneral itemGeneral = generalRows.stream()
                            .filter(r -> testCaseId.equalsIgnoreCase(r.getTestCaseId()))
                            .findFirst()
                            .orElseThrow(() ->
                                    new IllegalStateException("No ItemGeneral found for TestCaseId: " + testCaseId));

                    ItemCategoryDescription itemCategoryDescription = categoryDescRows.stream()
                            .filter(r -> testCaseId.equalsIgnoreCase(r.getTestCaseId()))
                            .findFirst()
                            .orElse(null);

                    ItemOthers itemOthers = otherRows.stream()
                            .filter(r -> testCaseId.equalsIgnoreCase(r.getTestCaseId()))
                            .findFirst()
                            .orElse(null);

                    ItemPricing itemPricing = pricingRows.stream()
                            .filter(r -> testCaseId.equalsIgnoreCase(r.getTestCaseId()))
                            .findFirst()
                            .orElse(null);

                    ItemInventoryTracking itemInventoryTracking = trackingRows.stream()
                            .filter(r -> testCaseId.equalsIgnoreCase(r.getTestCaseId()))
                            .findFirst()
                            .orElse(null);

                    ItemPOSBehaviour itemPOSBehaviour = posBehaviourRows.stream()
                            .filter(r -> testCaseId.equalsIgnoreCase(r.getTestCaseId()))
                            .findFirst()
                            .orElse(null);

                    List<ItemStoreWiseMultiplePrice> itemStoreWiseMultiplePriceList =
                            storeWisePriceRows.stream()
                                    .filter(r -> testCaseId.equalsIgnoreCase(r.getTestCaseId()))
                                    .collect(Collectors.toList());

                    int totalInvocations =
                            Optional.ofNullable(testCase.getInvocationcount())
                                    .orElse(1);

                    if (totalInvocations < 1) {
                        totalInvocations = 1;
                    }

                    int finalTotalInvocations = totalInvocations;

                    return IntStream.rangeClosed(1, finalTotalInvocations)
                            .mapToObj(invIndex -> {

                                MergedInventoryItemData data =
                                        MergedInventoryItemData.builder()
                                                .masterTestData(testCase)
                                                .itemGeneral(itemGeneral)
                                                .itemCategoryDescription(itemCategoryDescription)
                                                .itemOthers(itemOthers)
                                                .itemPricing(itemPricing)
                                                .itemInventoryTracking(itemInventoryTracking)
                                                .itemPOSBehaviour(itemPOSBehaviour)
                                                .storeWiseMultiplePriceList(itemStoreWiseMultiplePriceList)
                                                .invocationIndex(invIndex)
                                                .totalInvocations(finalTotalInvocations)
                                                .build();

                                // Mandatory validation
                                MandatoryFieldValidator.validate(data.getItemGeneral());

                                if (data.getItemCategoryDescription() != null) {
                                    MandatoryFieldValidator.validate(data.getItemCategoryDescription());
                                }
                                if (data.getItemInventoryTracking() != null) {
                                    MandatoryFieldValidator.validate(data.getItemInventoryTracking());
                                }
                                if (data.getItemOthers() != null) {
                                    MandatoryFieldValidator.validate(data.getItemOthers());
                                }
                                if (data.getItemPricing() != null) {
                                    MandatoryFieldValidator.validate(data.getItemPricing());
                                }
                                if (data.getItemPOSBehaviour() != null) {
                                    MandatoryFieldValidator.validate(data.getItemPOSBehaviour());
                                }

                                return data;
                            });
                });
    }

    /* ---------- debug runner (no TestNG required) ---------- */
    /** Ad hoc manual runner — invokes {@link #dataSupplier} via {@link #dummyMethod} and prints each resulting row's invocation index, without needing a TestNG run. */
    public static void main(String[] args) throws Exception {

        MergedInvItemDataSupplier supplier = new MergedInvItemDataSupplier();

        supplier.dataSupplier(
                MergedInvItemDataSupplier.class.getDeclaredMethod("dummyMethod")
        ).forEach(data ->
                System.out.println(
                        "TC=" + data.getMasterTestData().getTestcaseid() +
                                "  Inv=" + data.getInvocationIndex() +
                                "/" + data.getTotalInvocations()
                )
        );
    }

    // dummy method required only to satisfy Method parameter
    /** Placeholder {@link Method} target for {@link #main}'s manual invocation — never actually run by TestNG. */
    public void dummyMethod() {}
}
