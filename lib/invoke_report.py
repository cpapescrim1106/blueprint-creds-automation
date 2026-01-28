import boto3
import json
import os
import sys
import argparse
from dotenv import load_dotenv

def main():
    parser = argparse.ArgumentParser(description="Replay a report request to AWS SQS using harvested credentials.")
    parser.add_argument("--payload", required=True, help="Path to the JSON file containing the message body.")
    parser.add_argument("--queue", help="SQS Queue Name or URL. If not provided, tries to guess from env or defaults.")
    parser.add_argument("--prefix", help="Prefix filter when listing queues (e.g., FL_acc).")
    parser.add_argument("--env", default=".env.aws", help="Path to the .env.aws file.")
    
    args = parser.parse_args()

    # 1. Load Credentials
    if not os.path.exists(args.env):
        print(f"Error: Credentials file '{args.env}' not found.")
        print("Run './scripts/harvest_credentials.sh' first.")
        sys.exit(1)
    
    load_dotenv(args.env)
    
    access_key = os.getenv("AWS_ACCESS_KEY_ID")
    secret_key = os.getenv("AWS_SECRET_ACCESS_KEY")
    region = os.getenv("AWS_DEFAULT_REGION", "us-east-2")
    
    if not access_key or not secret_key:
        print("Error: AWS credentials missing in .env file.")
        sys.exit(1)

    print(f"Loaded credentials for region: {region}")

    # 2. Setup SQS Client
    sqs = boto3.client(
        'sqs',
        region_name=region,
        aws_access_key_id=access_key,
        aws_secret_access_key=secret_key
    )

    # 3. Determine Queue URL
    queue_target = args.queue
    if not queue_target:
        print(f"No queue specified. Listing queues{' with prefix ' + args.prefix if args.prefix else ''}...")
        try:
            kwargs = {}
            if args.prefix:
                kwargs['QueueNamePrefix'] = args.prefix
                
            response = sqs.list_queues(**kwargs)
            queues = response.get('QueueUrls', [])
            for q in queues:
                print(f"  - {q}")
            
            print("\nPlease specify one of the above with --queue")
            sys.exit(1)
        except Exception as e:
            print(f"Error listing queues: {e}")
            sys.exit(1)

    # Resolve Queue URL if a name was given
    if not queue_target.startswith("https://"):
        try:
            response = sqs.get_queue_url(QueueName=queue_target)
            queue_url = response['QueueUrl']
            print(f"Resolved queue name '{queue_target}' to URL: {queue_url}")
        except Exception as e:
            print(f"Error resolving queue name: {e}")
            sys.exit(1)
    else:
        queue_url = queue_target

    # 4. Read Payload
    with open(args.payload, 'r') as f:
        message_body = f.read()
    
    # Validate valid JSON (optional, but good practice)
    try:
        json_obj = json.loads(message_body)
        # If the file contains the captured wrapper (e.g. from the relay logs), we might need to extract just the body.
        # The relay log captures usually look like: {"args": ["...", "THE_PAYLOAD"]}
        # or raw depending on how we saved it.
        # For now, assume the user provides the raw inner JSON object needed by the server.
    except json.JSONDecodeError:
        print("Warning: Payload is not valid JSON. Sending as raw text.")

    # 5. Send Message
    print(f"Sending message to {queue_url}...")
    try:
        kwargs = {
            'QueueUrl': queue_url,
            'MessageBody': message_body
        }
        
        if queue_url.endswith(".fifo"):
            import time
            kwargs['MessageGroupId'] = "report_requests"
            kwargs['MessageDeduplicationId'] = str(time.time())
            
        response = sqs.send_message(**kwargs)
        print("✅ Message Sent!")
        print(f"Message ID: {response.get('MessageId')}")
    except Exception as e:
        print(f"❌ Failed to send message: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main()
