import boto3
import os
import sys
import argparse
import time
from dotenv import load_dotenv
from pathlib import Path

def main():
    parser = argparse.ArgumentParser(description="Download a report result from S3.")
    parser.add_argument("key", help="The S3 key (e.g., FL_acc_50_...)")
    parser.add_argument("--bucket", default="bp-temp-us", help="S3 bucket name.")
    parser.add_argument("--output", help="Output filename. Defaults to <key>.xml or <key>.zip")
    parser.add_argument("--env", default=".env.aws", help="Path to .env.aws file.")
    parser.add_argument("--wait", action="store_true", help="Wait/Poll for the file to appear.")
    
    args = parser.parse_args()

    # 1. Load Credentials
    if not os.path.exists(args.env):
        print(f"Error: Credentials file '{args.env}' not found.")
        sys.exit(1)
    
    load_dotenv(args.env)
    
    # Setup S3 Client
    s3 = boto3.client(
        's3',
        region_name=os.getenv("AWS_DEFAULT_REGION", "us-east-2"),
        aws_access_key_id=os.getenv("AWS_ACCESS_KEY_ID"),
        aws_secret_access_key=os.getenv("AWS_SECRET_ACCESS_KEY")
    )

    dest = Path(args.output if args.output else args.key)
    
    print(f"Downloading s3://{args.bucket}/{args.key} ...")

    # 2. Download loop (if --wait)
    attempts = 0
    max_attempts = 30 if args.wait else 1
    
    while attempts < max_attempts:
        try:
            s3.download_file(args.bucket, args.key, str(dest))
            print(f"✅ Successfully downloaded to: {dest}")
            
            # Check if it's a zip file
            if dest.suffix == "" or dest.suffix.lower() not in [".xml", ".pdf", ".zip"]:
                # Try to detect if it's XML or ZIP by reading header
                with open(dest, 'rb') as f:
                    header = f.read(4)
                    if header.startswith(b'PK\x03\x04'):
                        print("Detected ZIP format. Renaming to .zip")
                        dest.rename(dest.with_suffix(".zip"))
                    elif header.startswith(b'<?xm'):
                        print("Detected XML format. Renaming to .xml")
                        dest.rename(dest.with_suffix(".xml"))
            
            return
        except Exception as e:
            if "404" in str(e) and args.wait:
                attempts += 1
                print(f"[{attempts}/{max_attempts}] File not ready yet, waiting 2s...")
                time.sleep(2)
                continue
            else:
                print(f"❌ Error: {e}")
                sys.exit(1)

    print("❌ Timed out waiting for report.")
    sys.exit(1)

if __name__ == "__main__":
    main()
