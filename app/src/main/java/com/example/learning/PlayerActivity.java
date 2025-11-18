package com.example.learning;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlayerActivity extends AppCompatActivity {

    private Button backButton;
    private Button scanButton;
    private Button addProductButton;
    private TextView resultText;
    private TextView priceText;

    private static final int CAMERA_PERMISSION_REQUEST = 100;
    private Map<String, Product> productDatabase;
    private List<Product> productList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        initializeProductDatabase();
        initializeViews();
        setupClickListeners();
    }

    private void initializeProductDatabase() {
        productDatabase = new HashMap<>();
        productList = new ArrayList<>();

        addInitialProducts();
    }

    private void addInitialProducts() {
        Product[] initialProducts = {
                new Product("Milka Čokolada 100g", 2.50, "123456789012"),
                new Product("Coca-Cola 0.5L", 1.80, "978020137962"),
                new Product("Argeta Pašteta", 3.20, "590123412345"),
                new Product("Dukat Miljeko 1L", 1.50, "385123456789"),
                new Product("Brasno Tipo 00 1kg", 1.20, "387123456789"),
                new Product("Cedevita 250g", 4.50, "385987654321"),
                new Product("Nutella 350g", 5.80, "401440033982"),
                new Product("Nescafe Classic 100g", 6.20, "400590003078")
        };

        for (Product product : initialProducts) {
            productDatabase.put(product.getBarcode(), product);
            productList.add(product);
        }
    }

    private void initializeViews() {
        backButton = findViewById(R.id.backButton);
        scanButton = findViewById(R.id.scanButton);
        addProductButton = findViewById(R.id.addProductButton);
        resultText = findViewById(R.id.resultText);
        priceText = findViewById(R.id.priceText);
    }

    private void setupClickListeners() {
        backButton.setOnClickListener(v -> finish());

        scanButton.setOnClickListener(v -> {
            if (checkCameraPermission()) {
                startBarcodeScanner();
            } else {
                requestCameraPermission();
            }
        });

        addProductButton.setOnClickListener(v -> showAddProductDialog());
    }

    private void showAddProductDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Dodaj novi artikal");

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_product, null);
        builder.setView(dialogView);

        EditText productNameEditText = dialogView.findViewById(R.id.productNameEditText);
        EditText barcodeEditText = dialogView.findViewById(R.id.barcodeEditText);
        EditText priceEditText = dialogView.findViewById(R.id.priceEditText);

        builder.setPositiveButton("Dodaj", (dialog, which) -> {
            String name = productNameEditText.getText().toString().trim();
            String barcode = barcodeEditText.getText().toString().trim();
            String priceText = priceEditText.getText().toString().trim();

            if (validateProductInput(name, barcode, priceText)) {
                double price = Double.parseDouble(priceText);
                addNewProduct(name, price, barcode);
            }
        });

        builder.setNegativeButton("Otkaži", (dialog, which) -> {
            dialog.dismiss();
        });

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private boolean validateProductInput(String name, String barcode, String price) {
        if (name.isEmpty()) {
            Toast.makeText(this, "Unesite naziv artikla", Toast.LENGTH_SHORT).show();
            return false;
        }

        if (barcode.isEmpty()) {
            Toast.makeText(this, "Unesite barkod", Toast.LENGTH_SHORT).show();
            return false;
        }

        if (price.isEmpty()) {
            Toast.makeText(this, "Unesite cijenu", Toast.LENGTH_SHORT).show();
            return false;
        }

        if (productDatabase.containsKey(barcode)) {
            Toast.makeText(this, "Artikal sa ovim barkodom već postoji!", Toast.LENGTH_LONG).show();
            return false;
        }

        try {
            double priceValue = Double.parseDouble(price);
            if (priceValue <= 0) {
                Toast.makeText(this, "Cijena mora biti veća od 0", Toast.LENGTH_SHORT).show();
                return false;
            }
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Nevalidan format cijene", Toast.LENGTH_SHORT).show();
            return false;
        }

        return true;
    }

    private void addNewProduct(String name, double price, String barcode) {
        Product newProduct = new Product(name, price, barcode);
        productDatabase.put(barcode, newProduct);
        productList.add(newProduct);

        Toast.makeText(this, "Artikal uspješno dodat!", Toast.LENGTH_LONG).show();
        Log.d("Product", "Dodan novi artikal: " + name + " - " + barcode + " - " + price + " KM");

        resultText.setText("Skeniraj barkod za informacije o artiklu");
        priceText.setText("Cijena: --");
        priceText.setTextColor(getResources().getColor(android.R.color.white));
    }

    private boolean checkCameraPermission() {
        return ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{android.Manifest.permission.CAMERA},
                CAMERA_PERMISSION_REQUEST);
    }

    private void startBarcodeScanner() {
        IntentIntegrator integrator = new IntentIntegrator(this);
        integrator.setPrompt("Skeniraj barkod artikla");
        integrator.setBeepEnabled(true);
        integrator.setOrientationLocked(false);
        integrator.setDesiredBarcodeFormats(IntentIntegrator.ALL_CODE_TYPES);
        integrator.initiateScan();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startBarcodeScanner();
        } else {
            Toast.makeText(this, "Potrebna je dozvola za kameru", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null && result.getContents() != null) {
            processScannedBarcode(result.getContents());
        }
    }

    private void processScannedBarcode(String barcode) {
        Product product = productDatabase.get(barcode);

        if (product != null) {
            resultText.setText("Artikal: " + product.getName() + "\nBarkod: " + product.getBarcode());
            priceText.setText(String.format("Cijena: %.2f KM", product.getPrice()));
            priceText.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
            Toast.makeText(this, "Artikal pronađen!", Toast.LENGTH_SHORT).show();
        } else {
            resultText.setText("Barkod: " + barcode + "\nArtikal nije pronađen");
            priceText.setText("Cijena: N/A");
            priceText.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
            Toast.makeText(this, "Artikal nije u bazi", Toast.LENGTH_LONG).show();
        }
    }

    private static class Product {
        private String name;
        private double price;
        private String barcode;

        public Product(String name, double price, String barcode) {
            this.name = name;
            this.price = price;
            this.barcode = barcode;
        }

        public String getName() { return name; }
        public double getPrice() { return price; }
        public String getBarcode() { return barcode; }
    }
}