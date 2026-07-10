#!/usr/bin/env python3
"""
Generate demo transaction CSV for SpendWise demo account.
1 year of realistic transactions with patterns: salary, EMI, recurring bills, daily spending.
Output: data/demo-transactions.csv
"""

import csv
import random
from datetime import datetime, timedelta
from typing import List, Dict
import hashlib
import uuid

# Configuration
START_DATE = datetime(2025, 7, 1)
END_DATE = datetime(2026, 6, 30)
DEMO_USER_ID = "demo-user-12345678"  # Placeholder; will be replaced by backend

# Categories (map to category IDs from database)
CATEGORIES = {
    "Food / Dine Out": 7,
    "Groceries": 4,
    "Travel": 5,
    "Shopping": 1,
    "Subscriptions": 9,
    "Transfers": 10,
    "Miscellaneous": 6,
    "Medical": 11,
    "Fees & Debt": 12,
    "Entertainment": 2,
}

# Family contacts for transfers
FAMILY = ["Rahul Sharma", "Priya Verma", "Amit Kumar", "Shreya Patel", "Rajesh Singh"]
FAMILY_UPIIDS = ["rahul.sharma@upi", "priya.v@okaxis", "amit.k@ibl", "shreya.p@upi", "rajesh.s@ybl"]

# Merchants
FOOD_MERCHANTS = [
    ("Swiggy", "swiggy@okicici"),
    ("Zomato", "zomato@icic"),
    ("Dominos", "dominos@hdfc"),
    ("McDonald's", "mcdonalds@hdfc"),
    ("Cafe Coffee Day", "caffe.ccd@hdfc"),
    ("Local Dhaba", "dhaba.local@upi"),
    ("Subway", "subway@okaxis"),
    ("Starbucks", "starbucks@hdfc"),
]

TRAVEL_MERCHANTS = [
    ("Rapido", "rapido@okaxis"),
    ("Uber", "uber@hdfc"),
    ("Ola", "ola@icic"),
    ("Flipkart Flight", "flights@flipkart"),
    ("IRCTC", "irctc@sbin"),
    ("MakeMyTrip", "mmt@hdfc"),
    ("Fuel Station", "fuel.petrol@upi"),
]

SHOPPING_MERCHANTS = [
    ("Amazon", "amazon@okaxis"),
    ("Flipkart", "flipkart@hdfc"),
    ("Myntra", "myntra@okaxis"),
    ("H&M", "hm@hdfc"),
    ("Decathlon", "decathlon@hdfc"),
    ("Ajio", "ajio@okaxis"),
    ("Meesho", "meesho@upi"),
]

GROCERY_MERCHANTS = [
    ("BigBasket", "bigbasket@okaxis"),
    ("Blinkit", "blinkit@okaxis"),
    ("DMart", "dmart@hdfc"),
    ("Spencer", "spencer@hdfc"),
    ("Local Vegetable Vendor", "vendor@upi"),
]

SUBSCRIPTION_MERCHANTS = [
    ("Netflix", "netflix@hdfc", 199),
    ("Spotify", "spotify@okaxis", 69),
    ("Prime Video", "amazon@okaxis", 149),
    ("Gym Subscription", "gym@upi", 500),
]

# Banks for salary & EMI
BANKS = ["SBIN", "HDFC", "ICIC", "AXIS", "KOTAK"]

# Transaction modes
TRANSACTION_MODES = ["UPI", "INB", "NEFT", "IMPS"]

def generate_transaction_id(user_id: str, upi_id: str, amount: float, date: datetime) -> str:
    """Generate a unique transaction_id similar to backend logic."""
    # Use SHA-256 hash of user_id + upi_id + amount + date (minute precision)
    key = f"{user_id}|{upi_id}|{amount}|{date.strftime('%Y-%m-%d %H:%M')}"
    hash_val = hashlib.sha256(key.encode()).hexdigest()[:16]
    return f"txn_{hash_val}"

def generate_transactions() -> List[Dict]:
    """Generate 1 year of demo transactions."""
    transactions = []
    current_date = START_DATE

    # Track which months have Netflix (every 3 months)
    netflix_months = [7, 10, 1, 4]  # Jul, Oct, Jan, Apr (every 3 months starting July)
    gas_months = set()  # Alternate months for gas bill
    for m in range(7, 13):
        if (m - 7) % 2 == 0:
            gas_months.add(m)
    for m in range(1, 7):
        if (m - 1) % 2 == 0:
            gas_months.add(m)

    month_tracker = {}  # Track what we've added per month

    while current_date <= END_DATE:
        year = current_date.year
        month = current_date.month
        month_key = f"{year}-{month:02d}"

        if month_key not in month_tracker:
            month_tracker[month_key] = {
                "salary": False,
                "emi": False,
                "spotify": False,
                "light_bill": False,
                "gas_bill": False,
                "netflix": False,
            }

        # === RECURRING FIXED TRANSACTIONS ===

        # Monthly salary (25th)
        if current_date.day == 25 and not month_tracker[month_key]["salary"]:
            transactions.append({
                "transaction_date": datetime(year, month, 25, 9, 30, 0),
                "recipient_name": random.choice(BANKS),
                "upi_id": f"salary.{month}@{random.choice(BANKS).lower()}",
                "amount": 75000,
                "dr_cr_indicator": "CR",
                "debit": 0,
                "credit": 75000,
                "transaction_mode": "INB",
                "bank": random.choice(BANKS),
                "category": None,  # Income, no category
                "note": "Monthly Salary",
            })
            month_tracker[month_key]["salary"] = True

        # Car EMI (28th)
        if current_date.day == 28 and not month_tracker[month_key]["emi"]:
            transactions.append({
                "transaction_date": datetime(year, month, 28, 14, 0, 0),
                "recipient_name": "Car Loan EMI",
                "upi_id": "carloan@hdfc",
                "amount": -5000,
                "dr_cr_indicator": "DR",
                "debit": 5000,
                "credit": 0,
                "transaction_mode": "UPI",
                "bank": "HDFC",
                "category": CATEGORIES["Fees & Debt"],
                "note": "Car Loan EMI",
            })
            month_tracker[month_key]["emi"] = True

        # Spotify (27th)
        if current_date.day == 27 and not month_tracker[month_key]["spotify"]:
            transactions.append({
                "transaction_date": datetime(year, month, 27, 10, 15, 0),
                "recipient_name": "Spotify",
                "upi_id": "spotify@okaxis",
                "amount": -69,
                "dr_cr_indicator": "DR",
                "debit": 69,
                "credit": 0,
                "transaction_mode": "UPI",
                "bank": "OKAXIS",
                "category": CATEGORIES["Subscriptions"],
                "note": "Spotify Premium",
            })
            month_tracker[month_key]["spotify"] = True

        # Light bill (26-30th, random day)
        if 26 <= current_date.day <= 30 and not month_tracker[month_key]["light_bill"]:
            if random.random() < 0.15:  # Only add on some days
                amount = random.randint(1500, 2000)
                transactions.append({
                    "transaction_date": datetime(year, month, current_date.day, 11, 0, 0),
                    "recipient_name": "Electricity Board",
                    "upi_id": "electricity@sbin",
                    "amount": -amount,
                    "dr_cr_indicator": "DR",
                    "debit": amount,
                    "credit": 0,
                    "transaction_mode": "UPI",
                    "bank": "SBIN",
                    "category": CATEGORIES["Miscellaneous"],
                    "note": "Monthly Electricity Bill",
                })
                month_tracker[month_key]["light_bill"] = True

        # Gas bill (alternate months, 1-15th)
        if month in gas_months and 1 <= current_date.day <= 15 and not month_tracker[month_key]["gas_bill"]:
            if random.random() < 0.10:
                amount = random.randint(1000, 1200)
                transactions.append({
                    "transaction_date": datetime(year, month, current_date.day, 12, 30, 0),
                    "recipient_name": "Gas Agency",
                    "upi_id": "gas@upi",
                    "amount": -amount,
                    "dr_cr_indicator": "DR",
                    "debit": amount,
                    "credit": 0,
                    "transaction_mode": "UPI",
                    "bank": "UPI",
                    "category": CATEGORIES["Miscellaneous"],
                    "note": "Gas Bill",
                })
                month_tracker[month_key]["gas_bill"] = True

        # Netflix (once every 3 months)
        if month in netflix_months and current_date.day == random.randint(1, 5) and not month_tracker[month_key]["netflix"]:
            transactions.append({
                "transaction_date": datetime(year, month, current_date.day, 9, 0, 0),
                "recipient_name": "Netflix",
                "upi_id": "netflix@hdfc",
                "amount": -199,
                "dr_cr_indicator": "DR",
                "debit": 199,
                "credit": 0,
                "transaction_mode": "UPI",
                "bank": "HDFC",
                "category": CATEGORIES["Subscriptions"],
                "note": "Netflix Subscription",
            })
            month_tracker[month_key]["netflix"] = True

        # === VARIABLE DAILY TRANSACTIONS ===

        # Food (3-4 times per week, 50-500 each)
        if random.random() < 0.5:
            merchant, upi_id = random.choice(FOOD_MERCHANTS)
            amount = random.randint(50, 500)
            transactions.append({
                "transaction_date": current_date.replace(hour=random.randint(12, 22), minute=random.randint(0, 59)),
                "recipient_name": merchant,
                "upi_id": upi_id,
                "amount": -amount,
                "dr_cr_indicator": "DR",
                "debit": amount,
                "credit": 0,
                "transaction_mode": "UPI",
                "bank": random.choice(BANKS),
                "category": CATEGORIES["Food / Dine Out"],
                "note": None,
            })

        # Travel/Rapido (2-3 times per week, 30-300)
        if random.random() < 0.4:
            merchant, upi_id = random.choice(TRAVEL_MERCHANTS)
            amount = random.randint(30, 300)
            transactions.append({
                "transaction_date": current_date.replace(hour=random.randint(7, 21), minute=random.randint(0, 59)),
                "recipient_name": merchant,
                "upi_id": upi_id,
                "amount": -amount,
                "dr_cr_indicator": "DR",
                "debit": amount,
                "credit": 0,
                "transaction_mode": "UPI",
                "bank": random.choice(BANKS),
                "category": CATEGORIES["Travel"],
                "note": None,
            })

        # Groceries (1-2 times per week, 200-800)
        if random.random() < 0.25:
            merchant, upi_id = random.choice(GROCERY_MERCHANTS)
            amount = random.randint(200, 800)
            transactions.append({
                "transaction_date": current_date.replace(hour=random.randint(9, 20), minute=random.randint(0, 59)),
                "recipient_name": merchant,
                "upi_id": upi_id,
                "amount": -amount,
                "dr_cr_indicator": "DR",
                "debit": amount,
                "credit": 0,
                "transaction_mode": "UPI",
                "bank": random.choice(BANKS),
                "category": CATEGORIES["Groceries"],
                "note": None,
            })

        # Shopping (1-2 times per month, 500-3000)
        if random.random() < 0.08:
            merchant, upi_id = random.choice(SHOPPING_MERCHANTS)
            amount = random.randint(500, 3000)
            transactions.append({
                "transaction_date": current_date.replace(hour=random.randint(10, 22), minute=random.randint(0, 59)),
                "recipient_name": merchant,
                "upi_id": upi_id,
                "amount": -amount,
                "dr_cr_indicator": "DR",
                "debit": amount,
                "credit": 0,
                "transaction_mode": "UPI",
                "bank": random.choice(BANKS),
                "category": CATEGORIES["Shopping"],
                "note": None,
            })

        # Transfers to family (1-2 per month)
        if random.random() < 0.05:
            name = random.choice(FAMILY)
            upi_id = random.choice(FAMILY_UPIIDS)
            amount = random.randint(1000, 5000)
            transactions.append({
                "transaction_date": current_date.replace(hour=random.randint(9, 18), minute=random.randint(0, 59)),
                "recipient_name": name,
                "upi_id": upi_id,
                "amount": -amount,
                "dr_cr_indicator": "DR",
                "debit": amount,
                "credit": 0,
                "transaction_mode": "UPI",
                "bank": random.choice(BANKS),
                "category": CATEGORIES["Transfers"],
                "note": f"Transfer to {name}",
            })

        # Money received from family (1-2 per month)
        if random.random() < 0.03:
            name = random.choice(FAMILY)
            upi_id = random.choice(FAMILY_UPIIDS)
            amount = random.randint(500, 3000)
            transactions.append({
                "transaction_date": current_date.replace(hour=random.randint(9, 18), minute=random.randint(0, 59)),
                "recipient_name": name,
                "upi_id": upi_id,
                "amount": amount,
                "dr_cr_indicator": "CR",
                "debit": 0,
                "credit": amount,
                "transaction_mode": "UPI",
                "bank": random.choice(BANKS),
                "category": CATEGORIES["Transfers"],
                "note": f"Received from {name}",
            })

        # Miscellaneous/random spending (few per month)
        if random.random() < 0.02:
            misc_names = ["Medical Store", "Pharmacy", "Hardware Store", "Bookstore", "Salon", "Gym"]
            merchant = random.choice(misc_names)
            amount = random.randint(100, 1500)
            transactions.append({
                "transaction_date": current_date.replace(hour=random.randint(10, 19), minute=random.randint(0, 59)),
                "recipient_name": merchant,
                "upi_id": f"{merchant.lower().replace(' ', '')}@upi",
                "amount": -amount,
                "dr_cr_indicator": "DR",
                "debit": amount,
                "credit": 0,
                "transaction_mode": "UPI",
                "bank": random.choice(BANKS),
                "category": random.choice([CATEGORIES["Medical"], CATEGORIES["Miscellaneous"], CATEGORIES["Shopping"]]),
                "note": None,
            })

        # === MISCATEGORIZED (few throughout year) ===
        if random.random() < 0.003:  # Very rare
            merchant = "Random Vendor"
            amount = random.randint(100, 500)
            transactions.append({
                "transaction_date": current_date.replace(hour=random.randint(10, 19), minute=random.randint(0, 59)),
                "recipient_name": merchant,
                "upi_id": "unknown@upi",
                "amount": -amount,
                "dr_cr_indicator": "DR",
                "debit": amount,
                "credit": 0,
                "transaction_mode": "UPI",
                "bank": random.choice(BANKS),
                "category": CATEGORIES["Miscellaneous"],  # Will be marked as uncategorized
                "note": None,
            })

        current_date += timedelta(days=1)

    return transactions

def write_csv(transactions: List[Dict], filename: str):
    """Write transactions to CSV file in ingest format."""
    # Sort by date
    transactions.sort(key=lambda t: t["transaction_date"])

    fieldnames = [
        "transaction_date",
        "debit",
        "credit",
        "amount",
        "dr_cr_indicator",
        "transaction_id",
        "recipient_name",
        "upi_id",
        "bank",
        "transaction_mode",
        "note",
        "source",
        "category",  # Extra column for categorization (not in ingest, but useful for reference)
    ]

    with open(filename, 'w', newline='', encoding='utf-8') as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()

        for txn in transactions:
            # Generate transaction_id
            txn_id = generate_transaction_id(
                DEMO_USER_ID,
                txn["upi_id"],
                txn["amount"],
                txn["transaction_date"]
            )

            writer.writerow({
                "transaction_date": txn["transaction_date"].isoformat() + "Z",
                "debit": txn["debit"],
                "credit": txn["credit"],
                "amount": txn["amount"],
                "dr_cr_indicator": txn["dr_cr_indicator"],
                "transaction_id": txn_id,
                "recipient_name": txn["recipient_name"],
                "upi_id": txn["upi_id"],
                "bank": txn["bank"],
                "transaction_mode": txn["transaction_mode"],
                "note": txn["note"] or "",
                "source": "bank_statement",
                "category": txn.get("category") or "",
            })

if __name__ == "__main__":
    print("Generating demo transactions...")
    transactions = generate_transactions()
    print(f"Generated {len(transactions)} transactions")

    output_file = "demo-transactions.csv"
    write_csv(transactions, output_file)
    print(f"Written to {output_file}")
    print(f"Sample rows:")
    with open(output_file, 'r') as f:
        lines = f.readlines()
        print(''.join(lines[:5]))
