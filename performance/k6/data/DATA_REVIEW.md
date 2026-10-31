# k6 data review (read-only trace, nothing deleted)

## data/business: files, readers, keys no reader mentions

### dc.json  readers: flows/createDc.js
- qafeature.dc.json (474 B)  keys=21, unmentioned-by-readers=['I']
- qahbros.dc.json (471 B)  keys=21, unmentioned-by-readers=['I']
- stage-ideal.dc.json (486 B)  keys=22, unmentioned-by-readers=['I']

### ecommerceBillingByBrand.json  readers: flows/importEcommerceBilling.js
- qa-erp-mt-01.ecommerceBillingByBrand.json (32300 B)  keys=51, unmentioned-by-readers=['basicRateIn', 'callFrom', 'checkDuplicateRow', 'classPropertyName', 'code', 'companyCode', 'dataType', 'defaultValue', 'discountBasis', 'discountFactor', 'discountMode', 'entryMode', 'excelFieldName', 'formCode', '+23 more']
- qafeature.ecommerceBillingByBrand.json (31090 B)  keys=51, unmentioned-by-readers=['basicRateIn', 'callFrom', 'checkDuplicateRow', 'classPropertyName', 'code', 'companyCode', 'dataType', 'defaultValue', 'discountBasis', 'discountFactor', 'discountMode', 'entryMode', 'excelFieldName', 'formCode', '+23 more']

### generalVoucher.json  readers: flows/createGeneralVoucher.js, flows/fillDocSchemeVoucher.js
- clone-stage-ideal.generalVoucher.json (1492 B)  keys=32, unmentioned-by-readers=['admouCode']
- stage-ideal.generalVoucher.json (1492 B)  keys=32, unmentioned-by-readers=['admouCode']
  !! byte-identical: clone-stage-ideal.generalVoucher.json, stage-ideal.generalVoucher.json

### grc.json  readers: flows/createGrc.js, scenarios/backfill/createGrcScaled.js
- qafeature.grc.json (361 B)  keys=16, unmentioned-by-readers=none
- qahbros.grc.json (359 B)  keys=16, unmentioned-by-readers=none
- stage-ideal.grc.json (363 B)  keys=16, unmentioned-by-readers=none

### item.json  readers: flows/createItem.js
- qafeature.item.json (8039 B)  keys=35, unmentioned-by-readers=['B', 'S', 'barcodePrefix', 'cName', 'datatype', 'fieldName', 'govtIdentifier', 'lev1GrpName', 'lev2GrpName', 'value']
- qahbros.item.json (815 B)  keys=31, unmentioned-by-readers=['B', 'S', 'barcodePrefix', 'cName', 'govtIdentifier', 'lev1GrpName', 'lev2GrpName']
- stage-ideal.item.json (7349 B)  keys=34, unmentioned-by-readers=['B', 'S', 'barcodePrefix', 'cName', 'datatype', 'fieldName', 'govtIdentifier', 'lev1GrpName', 'lev2GrpName', 'value']

### serviceInvoiceAdhoc.json  readers: flows/serviceInvoiceAdhoc.js, lib/config.js
- qafeature.serviceInvoiceAdhoc.json (15669 B)  keys=175, unmentioned-by-readers=['abbreviation', 'address', 'admCurrencyCode', 'admGstinCode', 'admou_Code', 'agCode', 'agRate', 'allowAuthorizeOfSelfCreatedTransaction', 'appAmount', 'baddr', 'basis', 'chargeAmount', 'chargeCode', 'chargeName', '+91 more']
- qahbros.serviceInvoiceAdhoc.json (15653 B)  keys=175, unmentioned-by-readers=['abbreviation', 'address', 'admCurrencyCode', 'admGstinCode', 'admou_Code', 'agCode', 'agRate', 'allowAuthorizeOfSelfCreatedTransaction', 'appAmount', 'baddr', 'basis', 'chargeAmount', 'chargeCode', 'chargeName', '+91 more']
- stage-ideal.serviceInvoiceAdhoc.json (45983 B)  keys=175, unmentioned-by-readers=['abbreviation', 'address', 'admCurrencyCode', 'admGstinCode', 'admou_Code', 'agCode', 'agRate', 'allowAuthorizeOfSelfCreatedTransaction', 'appAmount', 'baddr', 'basis', 'chargeAmount', 'chargeCode', 'chargeName', '+91 more']

### site.json  readers: flows/allocateItemToCloudMposSite.js, flows/createCloudMposSite.js
- qahbros.site.json (645 B)  keys=23, unmentioned-by-readers=none

### transferOut.json  readers: flows/createTransferOut.js
- qafeature.transferOut.json (261 B)  keys=13, unmentioned-by-readers=none
- qahbros.transferOut.json (261 B)  keys=13, unmentioned-by-readers=none
- stage-ideal.transferOut.json (240 B)  keys=12, unmentioned-by-readers=none

### transferOut.zip  readers: NONE FOUND
- qahbros.transferOut.zip (12957 B)

### user.json  readers: scenarios/backfill/seedPerfUsers.js, scenarios/performance/verifyPerfUsers.js
- qafeature.user.json (94 B)  keys=4, unmentioned-by-readers=none
- qahbros.user.json (94 B)  keys=4, unmentioned-by-readers=none
- qaprod.user.json (520 B)  keys=15, unmentioned-by-readers=['DefaultOU', 'DefaultSite', 'Ouid', 'Siteid', 'UserId', 'deptCode', 'profCode', 'reportRole', 'roleId', 'sites']
  !! byte-identical: qafeature.user.json, qahbros.user.json

## erp/config + other files and who references them
- erp/config/multiTenantRun.billingImport.json (306 B): k6=['scripts/run-billing-import-2tenants.bat'] java=none
- erp/config/multiTenantRun.itemChain.json (291 B): k6=none java=none
- erp/config/multiTenantRun.json (250 B): k6=['scripts/run-multi-tenant.ps1'] java=none
- erp/config/multiTenantRun.testLoginLogout.json (246 B): k6=none java=none
- data/templates/customerCreate.json (3017 B): k6=none java=['src/test/java/com/tests/API/K6ContractTest.java']
- data/templates/vendorMasterCreate.json (3047 B): k6=none java=['src/test/java/com/tests/API/K6ContractTest.java']
- data/business/qahbros.transferOut.zip (12957 B): k6=none java=none

## attachments (tracked in git)
- AP Voucher Creation(Multi)without Adjustment (1).xlsx: 0.01 MB sha=d0dbc1c493 referenced by ['clone-stage-ideal.generalVoucher.json', 'stage-ideal.generalVoucher.json']
- APVoucherNumericUDF.png: 0.06 MB sha=f4d096ec30 referenced by ['clone-stage-ideal.generalVoucher.json', 'stage-ideal.generalVoucher.json']
- Volume-1-Architecture-and-Foundation.docx: 0.04 MB sha=6171b0b7f6 referenced by ['clone-stage-ideal.generalVoucher.json', 'stage-ideal.generalVoucher.json']
- Volume1.pdf: 10.02 MB sha=a27b544cc4 referenced by ['clone-stage-ideal.generalVoucher.json', 'stage-ideal.generalVoucher.json']

## pools
- stage-ideal.items.json (155 KB): reader lib/itemPool.js uses data/pools/${TENANT}.items.json -> only tenants with a pool file work

## tenant json keys
- clone-stage-ideal.json businessDataFile=clone-stage-ideal.generalVoucher.json exists=True
- qa-erp-mt-01.json businessDataFile=qa-erp-mt-01.ecommerceBillingByBrand.json exists=True
- qafeature.json businessDataFile=qafeature.serviceInvoiceAdhoc.json exists=True
- qahbros.json businessDataFile=qahbros.serviceInvoiceAdhoc.json exists=True
- qaprod.json businessDataFile=qaprod.serviceInvoiceAdhoc.json exists=False
- stage-ideal.json businessDataFile=stage-ideal.serviceInvoiceAdhoc.json exists=True